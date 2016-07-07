---
title: 7周7并发模型 P6 - Data Parallelism with GPU
date: 2016-07-07 02:27:25
tags: [并发,Parallelism,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 6. Data Parallelism with GPU

<!-- more -->

### GPGPU Programming Basis

- The amount of data that needs to be processed is huge, the actual operations on that data are relatively simple vector or matrix operations.
- This makes them very amenable to data parallelization, in which multiple computing units perform the same operations on different items of data in parallel.
- GPUs combine pipelining and multiple ALUs with a wide range of other techniques, unfortunately, which’s little commonality between different GPUs.
- OpenCL targets multiple architectures by defining a C-like language that allows us to express a parallel algorithm abstractly.
- Divide your problem into the smallest workitems you can, OpenCL compiler and runtime then worry about how best to schedule those work-items on the available hardware.

### Our First OpenCL Program - Pair-wise Multiply

1. Implement a kernel which is the core work item algorithm.
2. Create a context within which the kernel will run together with a command queue.
3. Compile the kernel.
4. Create buffers for input and output data.
5. Enqueue a command that executes the kernel once for each work-item.
6. Retrieve the results.

##### Kernel

``` c
__kernel void multiply_arrays(__global const float* inputA,
                              __global const float* inputB,
                              __global float* output) {
  int i = get_global_id(0);
  output[i] = inputA[i] * inputB[i];
}
```

The OpenCL standard defines both C and C++ bindings. However, unofficial bindings are available for most major languages. We stick to C here.

It calls get_global_id() to determine which work-item it’s handling.

##### Context and Command Queue

``` c
  cl_platform_id platform;
  clGetPlatformIDs(1, &platform, NULL);

  cl_device_id device;
  clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, NULL);

  cl_context context = clCreateContext(NULL, 1, &device, NULL, NULL, NULL);
  cl_command_queue queue = clCreateCommandQueue(context, device, 0, NULL);
```

We want a simple context that only contains a single GPU, so after identifying a platform with clGetPlatformIDs(), we pass CL_DEVICE_TYPE_GPU to clGetDeviceIDs() to get the ID of a GPU.

The clCreateCommandQueue() method takes a context and a device and returns a queue that enables commands to be sent to that device.

##### Compile the Kernel

``` c
  char* source = read_source("multiply_arrays.cl");
  cl_program program = clCreateProgramWithSource(context, 1, (const char**)&source, NULL, NULL);
  free(source);
  clBuildProgram(program, 0, NULL, NULL, NULL, NULL);
  cl_kernel kernel = clCreateKernel(program, "multiply_arrays", NULL);
```

##### Create Buffers

``` c
  #define NUM_ELEMENTS 1024

  cl_float a[NUM_ELEMENTS], b[NUM_ELEMENTS];
  random_fill(a, NUM_ELEMENTS);
  random_fill(b, NUM_ELEMENTS);
  cl_mem inputA = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, sizeof(cl_float) * NUM_ELEMENTS, a, NULL);
  cl_mem inputB = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, sizeof(cl_float) * NUM_ELEMENTS, b, NULL);
  cl_mem output = clCreateBuffer(context, CL_MEM_WRITE_ONLY, sizeof(cl_float) * NUM_ELEMENTS, NULL, NULL);
```

##### Execute the Work Items

``` c
  clSetKernelArg(kernel, 0, sizeof(cl_mem), &inputA);
  clSetKernelArg(kernel, 1, sizeof(cl_mem), &inputB);
  clSetKernelArg(kernel, 2, sizeof(cl_mem), &output);

  size_t work_units = NUM_ELEMENTS;
  clEnqueueNDRangeKernel(queue, kernel, 1, NULL, &work_units, NULL, 0, NULL, NULL);
```

First set the kernel’s arguments with clSetKernelArg(), then clEnqueueNDRangeKernel() queues an N-dimensional range (NDRange) of work-items.

In our case, N is 1 (the 3rd argument to clEnqueueNDRangeKernel()) and the number of work-items is 1,024 (NUM_ELEMENTS).

##### Retrieve Results and Clean up

``` c
  cl_float results[NUM_ELEMENTS];
  clEnqueueReadBuffer(queue, output, CL_TRUE, 0, sizeof(cl_float) * NUM_ELEMENTS, results, 0, NULL, NULL);

  clReleaseMemObject(inputA);
  clReleaseMemObject(inputB);
  clReleaseMemObject(output);
  clReleaseKernel(kernel);
  clReleaseProgram(program);
  clReleaseCommandQueue(queue);
  clReleaseContext(context);  
```

We create the results array and copy from the output buffer with the clEnqueueReadBuffer() function.

##### Profiling

Simply change the last parameter of clEnqueueNDRangeKernel() to enable profiling, as below:

``` c
  cl_event timing_event;
  size_t work_units = NUM_ELEMENTS;
  clEnqueueNDRangeKernel(queue, kernel, 1, NULL, &work_units, NULL, 0, NULL, &timing_event);
  cl_float results[NUM_ELEMENTS];
  clEnqueueReadBuffer(queue, output, CL_TRUE, 0, sizeof(cl_float) * NUM_ELEMENTS, results, 0, NULL, NULL);

  cl_ulong starttime;
  clGetEventProfilingInfo(timing_event, CL_PROFILING_COMMAND_START, sizeof(cl_ulong), &starttime, NULL);  
  cl_ulong endtime;
  clGetEventProfilingInfo(timing_event, CL_PROFILING_COMMAND_END, sizeof(cl_ulong), &endtime, NULL);  
  printf("Elapsed (GPU): %lu ns\n\n", (unsigned long)(endtime - starttime));
  clReleaseEvent(timing_event);
```

For this task, the GPU is more than nine times faster than a single CPU core.

##### What if there are multiple devices

To get fix number devices
``` c
cl_device_id devices[8];
cl_uint num_devices;
clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 8, devices, &num_devices);
```

num_devices will have been set to the number of available devices, and the first num_devices entries of the devices array will have been filled in.

This works fine, but what if there are more than eight available devices?
``` c
cl_uint num_devices;
clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, NULL, &num_devices);

cl_device_id* devices = (cl_device_id*)malloc(sizeof(cl_device_id) * num_devices);
clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, num_devices, devices, NULL);
```

No explaination needed.

##### Error handling

1. Some OpenCL functions return error codes, CL_SUCCESS indicates that the function succeeded; any other value indicates that it failed.

So some kind of utility function or macro to simplify the error handling process, for example:
``` c
#define CHECK_STATUS(s) do { \
    cl_int ss = (s); \
    if (ss != CL_SUCCESS) { \
        fprintf(stderr, "Error %d at line %d\n", ss, __LINE__); \
        exit(1); \
    } \
} while (0)
```

This allows us to write the following:
``` c
CHECK_STATUS(clSetKernelArg(kernel, 0, sizeof(cl_mem), &inputA));
```

2. Some other OpenCL functions take an error_ret parameter.

Here’s how we can call it with error handling:
``` c
cl_int status;
cl_context context = clCreateContext(NULL, 1, &device, NULL, NULL, &status);
CHECK_STATUS(status);
```


### Multidimensional Work-Item Ranges

When executing a kernel via clEnqueueNDRangeKernel(), an index space is defined where each point is identified by a unique global ID that represents a work-item.

A kernel can find the global ID of the work-item it’s executing by calling get_global_id().

In the 1st example, the index space is unidimensional, and therefore the kernel only needed to call get_global_id() once.

We will create a kernel that multiplies two-dimensional matrices and therefore calls get_global_id() twice.

##### Parallel Matrix Multiplication

kernel is as below:
``` c
__kernel void matrix_multiplication(uint widthA,
                                    __global const float* inputA,
                                    __global const float* inputB,
                                    __global float* output) {

  int i = get_global_id(0); 
  int j = get_global_id(1); 

  // Note that: outputWidth == widthB  &&  outputHeight == heightA  &&  widthA == heightB
  int outputWidth = get_global_size(0); 
  int outputHeight = get_global_size(1); 
  int widthB = outputWidth;

  float total = 0.0;
  for (int k = 0; k < widthA; ++k) { 
    total += inputA[j * widthA + k] * inputB[k * widthB + i];
  }
  output[j * outputWidth + i] = total;
}
```

1. the kernel calculate the (j, i) item of A * B
2. A, B & output Matrix are all save in a 1-dimensional array, instead of 2-demensional.
3. This kernel executes within a two-dimensional index space, each point of which identifies a location in the output array.
4. It can find out the range of the index space by calling get_global_size().
5. This also gives us widthB, which is equal to outputWidth, but we have to pass widthA as a parameter.

Which is to say, if A is M*K dimension, B is K*N dimension, then M & N are both global size, while K is a input parameter.

To execute the kernal:
``` c
size_t work_units[] = {WIDTH_OUTPUT, HEIGHT_OUTPUT};
CHECK_STATUS(clEnqueueNDRangeKernel(queue, kernel, 2, NULL, work_units, NULL, 0, NULL, NULL));
```

This creates a 2-dimensional index space by setting work_dim to 2 and specifies the extent of each dimension by setting global_work_size to work_units.


### Data-Parallel Reduce

##### Query Device Info

Uses clGetDeviceInfo() to query and print a device parameter with a value of type string.

To wrap a function to query device info,
``` c
void print_device_param_string(cl_device_id device, cl_device_info param_id, const char* param_name) {
    char value[1024];
    CHECK_STATUS(clGetDeviceInfo(device, param_id, sizeof(value), value, NULL));
    printf("%s: %s\n", param_name, value);
}

void print_device_info(cl_device_id device) {
    print_device_param_string(device, CL_DEVICE_NAME, "Name");
    print_device_param_string(device, CL_DEVICE_VENDOR, "Vendor");
    print_device_param_uint(device, CL_DEVICE_MAX_COMPUTE_UNITS, "Compute Units");
    print_device_param_ulong(device, CL_DEVICE_GLOBAL_MEM_SIZE, "Global Memory");
    print_device_param_ulong(device, CL_DEVICE_LOCAL_MEM_SIZE, "Local Memory");
    print_device_param_sizet(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, "Workgroup size");
}
```

So what is Compute Units and Workgroup size ? What is the difference between global and local memory ?

##### Platform Model

- An OpenCL platform consists of a host that’s connected to one or more devices.
- Each device has one or more compute units, each of which provides a number of processing elements.
- Work-items execute on processing elements. A collection of work-items executing on a single compute unit is a work-group.
- The work-items in a workgroup share local memory, which can be used for communication between work-items executing in that work-group.
- A single work-item has its Private memory.
- Global memory is the memory available to all work-items executing on a device.
- Constant memory is a region of global memory that remains constant during execution of a kernel.

```
So, Host => Devices => Compute Units => WorkGroup => WorkItem
            GlobalMem                   LocalMem     PrivateMem
```


##### A Single Work-Group Min()

To simplify, assume that the number of elements in the array we want to reduce is a power of two and small enough to be processed by a single work-group.

``` c
__kernel void find_minimum(__global const float* values,
                           __global float* result,
                           __local float* scratch) {
  int i = get_global_id(0);
  int n = get_global_size(0);
  scratch[i] = values[i]; 
  barrier(CLK_LOCAL_MEM_FENCE); 
  for (int j = n / 2; j > 0; j /= 2) { 
    if (i < j)
      scratch[i] = min(scratch[i], scratch[i + j]);
    barrier(CLK_LOCAL_MEM_FENCE); 
  } 
  if (i == 0)
    *result = scratch[0]; 
}
```

1. ATTENTION: all work-items are NOT running sequentially, but simultaneously! Say we have 8 elements in values, we will have 8 work-items running at the same time.
2. A barrier is a synchronization mechanism that allows work-items to coordinate their use of local memory.
3. If one work-item in a work-group executes barrier(), then all work-items in that work-group must execute the same barrier() before any of them can proceed beyond that point
  - It ensures that one work-item doesn’t start reducing until all work-items have copied their value from global to local memory
  - It ensures that one workitem doesn’t move on to loop iteration n + 1 until all work-items have finished loop iteration n.

For example, we have 8 elements in values, which are [35, 9, 1, 100, 83, 7, 28, 15], then the running steps will be:
```
    Step                         scratch[i=0]       scratch[i=1]       scratch[i=2]      scratch[i=3]  scratch[i=4]  scratch[i=5]  scratch[i=6]  scratch[i=7]
scratch[i] = values[i]               35                 9                   1                100            83            7             28           15
barrier(CLK_LOCAL_MEM_FENCE)  ------------------------------- wait all work-items to finish , then go the the next step -------------------------------------

1st loop: j = 4                 min(s[0], s[4])      min(s[1], s[5])    min(s[2], s[6])   min(s[3], s[7])     Do Nth.    Do Nth.     Do Nth.     Do Nth. 
                              = min(35, 83) = 35   = min(9, 7) = 7    = min(1, 28) = 1  = min(100, 15) = 15
barrier(CLK_LOCAL_MEM_FENCE)  ------------------------------- wait all work-items to finish , then go the the next step -------------------------------------
 
2nd loop: j = 2                 min(s[0], s[2])      min(s[1], s[3])       Do Nth.          Do Nth.           Do Nth.    Do Nth.     Do Nth.     Do Nth.
                              = min(35, 1) = 1     = min(7, 15) = 7
barrier(CLK_LOCAL_MEM_FENCE)  ------------------------------- wait all work-items to finish , then go the the next step -------------------------------------

3rd loop: j = 1                 min(s[0], s[1])          Do Nth.           Do Nth.          Do Nth.           Do Nth.    Do Nth.     Do Nth.     Do Nth.
                              = min(1, 7) = 1          
barrier(CLK_LOCAL_MEM_FENCE)  ------------------------------- wait all work-items to finish , then go the the next step -------------------------------------

if i == 0 {*result = scratch[0];}    1
```

##### A Multiple-Work-Group Min()

The above example works fine, but work-groups are restricted in size (such as no more than 1024 elements), so how to parallelize over multiple work-groups?

Extending our reduce across multiple work-groups is a simple matter of dividing the input array into work-groups and reducing each independently

If, for example, each work-group operates on 64 values at a time, this will reduce an array of N items to N/64 items. This smaller array can then be reduced in turn, and so on, until only a single result remains.

Each work-group has its local id and represents a section of a larger problem.
```
global id 0   
      |<---------------------------- global size -------------------------------------------->|
      |                                                                                       |
      [[      group 0      ],[      group 1      ],[      group 2      ],[      group 3      ]]
                             |<--- local size -->|
                             |                   |
                        local id 0
```

so, kernel will be modified as below:
``` c
__kernel void find_minimum(__global const float* values,
                           __global float* results,
                           __local float* scratch) {
  int i = get_local_id(0);
  int n = get_local_size(0);
  scratch[i] = values[get_global_id(0)];
  barrier(CLK_LOCAL_MEM_FENCE);
  for (int j = n / 2; j > 0; j /= 2) {
    if (i < j)
      scratch[i] = min(scratch[i], scratch[i + j]);
    barrier(CLK_LOCAL_MEM_FENCE);
  }
  if (i == 0)
    results[get_group_id(0)] = scratch[0];
}
```

1. This kernel is only for one iteration, but not the whole process, and the result of its work-group will be saved into results[get_group_id(0)].
2. To get the final result, we must run several iterations, and use results[] as the input parameter **values**, until one work-group is enough to hold values.
3. This kernel is for one group identified by get_group_id(0), and the work-item is identified by get_local_id(0)

To execute it
``` c
size_t work_units[] = {NUM_VALUES};
size_t workgroup_size[] = {WORKGROUP_SIZE};
CHECK_STATUS(clEnqueueNDRangeKernel(queue, kernel, 1, NULL, work_units, workgroup_size, 0, NULL, NULL));
```

