object Hello {
    def main(args: Array[String]): Unit = {
        test_basic
        test_sort_by_match
        test_merge_sort
        test_orderone_funcs
        test_adv_funcs
        test_obj_funcs
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic(): Unit = {
        print_banner("test basic")

        val l1 = "Hello"::("World"::Nil)    // 右->左匹配
        val l2 = "Hello"::"World"::Nil      // 和上面一样
        println(l1)
        println(l2)
        
        // 简易匹配赋值
        val List(a, b) = l1
        println("1st: " + a + "  2nd: " + b)
        val head :: rest = l2
        println("1st: " + head)
    }

    def test_sort_by_match(): Unit = {
        print_banner("test sort list by match")
        val old = List(6,2,5,7,1)
        println(sortlist(old))

        // 对 lst 排序
        def sortlist(lst : List[Int]): List[Int] = lst match {      // List[Int] 定义元素为 Int 的 List 类型
            case List() => List()
            case head :: rest => compute(head, sortlist(rest))      // 如果非空，那么递归排序尾部，并对头部和排序后的尾部进行位置调整
        }
    
        // 把元素 it 插入到排好序的 sortedlist 的恰当位置
        def compute(it: Int, sortedlist: List[Int]): List[Int] = sortedlist match {
            case List() => List(it)
            case head :: rest => if (it <= head) it :: sortedlist   // 如果非空，且头部大于待插入元素 it，那么把 it 摆在第一个位置上 
                else head :: compute(it, rest)                      // 否则，头部保持不变，并递归调用 compute，把元素 it 插入尾部的恰当位置
        }
    }

    def test_merge_sort():Unit = {
        print_banner("test merge sort")

        def mergesort[T](less:(T, T) => Boolean)(input:List[T]): List[T] = {    // curring 函数定义，参数分别是排序函数和待排序 List；并且使用了模版类型

            // 两个排好序的 List 的 merge 算法
            def merge(x:List[T], y:List[T]): List[T] = (x, y) match {
                case (Nil, _) => y
                case (_, Nil) => x
                case (xh :: xt, yh :: yt) =>  if (less(xh, yh)) xh :: merge(xt, y)    // closure，这里用到了外部的 less 排序函数
                    else yh :: merge(x, yt)
            }

            val n = input.length / 2            // 获取长度
            if (n == 0) input
            else {
                val (x, y) = input splitAt n    // 或者 input.splitAt(n)，在 n 位置出分开为两个 List
                merge(mergesort(less)(x), mergesort(less)(y))    // 对两个 List 分别递归排序后，在 merge 到一起
            }
        }

        println("asc order: " + mergesort((x:Int, y:Int) => x < y)(List(3,7,9,5,4)))
        val reverse_sort = mergesort((x:Int, y:Int) => x > y) _               // curring 对应的 partial 函数
        println("desc order: " + reverse_sort(List(3,7,9,5,4)))
    }

    def test_orderone_funcs():Unit = {
        print_banner("test order-1 functions")
        print("List(1,2,3):::List(4,5):::List():::List(7) becomes ")
        println(List(1,2,3):::List(4,5):::List():::List(7))    // list concatenation, output List(1, 2, 3, 4, 5, 7)
        // 以下函数中，都是生成一个新 List, 而原 List 保持不变
        val lst = List("1st", "2nd", "3rd")
        println(lst.last)   // 3rd
        println(lst.init)   // 除了 last 的前面所有部分，和 tail 的逻辑正好相反，output List(1st, 2nd)
        println(lst.reverse)  // List(3rd, 2nd, 1st)
        println(lst)        // 等价于 lst.toString, List(1st, 2nd, 3rd)
        println(lst.toString)  // List(1st, 2nd, 3rd)
        println(lst take 2)  // 等价于 lst.take(2)，output List(1st, 2nd)
        println(lst drop 2)  // List(3rd)
        println(lst splitAt 2)  // (List(1st, 2nd),List(3rd))
        println(lst apply 2)  // 等价于 lst(2), output 3rd
        println(lst(2))       // 3rd
        println(lst.indices)  // Range(0, 1, 2)，注意返回的不是 List，而是 Range
        println(lst.indices zip lst)  // Vector((0,1st), (1,2nd), (2,3rd))，注意返回的是 Vector
        println(lst.zipWithIndex)     // List((1st,0), (2nd,1), (3rd,2))，而这里返回的是 List

        println(lst.mkString("[", ",", "]"))   // [1st,2nd,3rd]
        println(lst.mkString("   "))   // 1st   2nd   3rd
        println(lst.mkString)          //  1st2nd3rd
        val buffer = new StringBuilder 
        lst addString(buffer, "(", ";;", ")")
        println(buffer)     // (1st;;2nd;;3rd)，看到，上面的这段代码，和 mkString 相当

        println(lst.toArray.toList)        // 注意，println(lst.toArray) 会 output 类似 [Ljava.lang.String;@5f184fc6
        val arr = new Array[String](5)
        lst.copyToArray(arr, 1)          // 从 arr 的 index=1 (从 0 开始) 位置开始，把 list copy 到 arr 中
        arr.foreach(println)             // null  1st  2nd  3rd  null

        val it = lst.toIterator
        println(it.next)           // 1st
        println(it.next)           // 2nd
        for (r <- it) println(r)   // 3rd
    }

    def test_adv_funcs():Unit = {
        print_banner("test advanced funcs")
        println(List("Hello", "World") map (_.toList.reverse.mkString))    // List(olleH, dlroW)
        println(List.range(1, 4) flatMap (i => List.range(1, i) map (j => (i, j))))   // flatMap, List.range, closure，output List((2, 1), (3, 1), (3, 2))

        // 类似的，以下函数中，都是生成一个新 List, 而原 List 保持不变
        val lst = List(1, 0, 1, 0, 2, 3)
        println(lst partition (_ % 2 == 0))    // List( List(0, 0, 2), List(1, 1, 3) )，按条件把 List 中元素分组
        println(lst find (_ % 2 == 0))   // 只返回满足条件的第一个 Some(0)
        println(lst find (_ < 0))        // None
        println(lst takeWhile (_ < 2))   // List(1,0,1,0)
        println(lst dropWhile (_ < 2))   // List(2, 3)
        println(lst span (_ < 1))        // List( List(), List(1, 0, 1, 0, 2, 3) )，和 partition 不同，span 遇到第一个不满足条件的位置就开始分组
        println(lst span (_ % 2 == 1))   // List( List(1), List(0, 1, 0, 2, 3) )
        
        println(List(List(1,0,0), List(0,1,0), List(0,0,0)) exists (row => row forall (_ == 0)))   // List of List 中，存在 List 元素，其每个子元素都是 0, output True

        // foldLeft
        println((1 to 100).foldLeft(0)(_+_)) // curring 化参数，第一个是累积初值，第二个是 fold 方法，该方法有两个参数，按顺序分别为累积值和当前遍历值
        println((0/:(1 to 100))(_+_))        // 和上面等价，output 5050
        println((1 to 5).foldLeft(100)(_-_)) // res=100-1=99 | res=99-2=97 | res=97-3=94 | res=94-4=90 | res=90-5=85 (或 100 - sum(1 to 5) = 85)

        // foldRight
        println((1 to 5).foldRight(100)(_-_)) // 等价于把 List 先倒序排列，然后调用 foldLeft，而 fold 方法中的参数位置也变换一下，先遍历值后累积值
        println(((1 to 5):\100)(_-_))         // 和上面等价，先做倒序，得到 5,4,3,2,1，然后 res=5-100=-95 | res=4-(-95)=99 | res=3-99=-96 | res=2-(-96)=98 |res=1-98=-97，搞定！
    }

    def test_obj_funcs():Unit = {
        print_banner("test class object functions")  // 上面都是 List 类中的函数调用，下面看看 List 伴生对象中的函数
        println(List.apply(1,2,3))    // 等价于 println(List(1,2,3))，输出就是 List(1,2,3)
        println(List.make(3,5))       // List(5,5,5)
        println(List.range(1,5))      // List(1,2,3,4)
        println(List.range(9, 1, -3)) // List(9, 6, 3)

        println(List(List(1,2), List(3),List(4,5)).flatten)  // List(1,2,3,4,5)
        println(List.concat(List(), List('b'), List('c')))   // List(b, c)
        println(List.map2(List(2, 5, 1), List(3, 4, 7))(_ * _))    // map2 对两个List 分别map，然后逐对调用方法参数；output(6, 20, 7)
    }

    def test_mutable():Unit = {
        // 前面的 case 中，List 都是 immutable 的，执行操作后生成新的 List，而不是 in-place 的修改
        // 这里看一下 mutable collection
        print_banner("test mutable collections")
        import scala.collection.mutable.ListBuffer
        val lb = new ListBuffer[Int]
        lb += 1          // 看到，val 类型仍然可以调整内部元素，不需要定义为 var
        lb += 2
        println(lb)      // ListBuffer(1, 2)?????

        import scala.collection.mutable.ArrayBuffer
        val ab = new ArrayBuffer[Int]()
        ab += 1
        ab += 2
        println(ab)      // ArrayBuffer(1, 2)?????

        // mutable queue
        val empty = Queue[Int]()
        val queue1 = empty.enqueue(1)
        val queue2 = queue1.enqueue(List(2,3,4))
        val (element, left) = queue2.dequeue
        println(element + " : " left)        // output 1 : Queue(2,3,4)  ??????

        // immutable queue
        import scala.collection.mutable.Queue
        val q = Queue[String]()
        q += "a"
        q ++= List("b", "c")
        q.dequeue
        println(q)      // Queue("b", "c") ????

        import scala.collection.mutable.Stack
        val stk = new Stack[Int]
        stk.push(1)
        stk.push(2)
        println(stk.top)    // 2
        println(stk.pop)    // 2
        println(stk)        // Stack(1) ?????
    }

    def test_map():Unit = {
        print_banner("test map")
        val data = mutable.Set.empty[Int]
        data ++= List(4,3,2)
        data += 1
        data --= List(2,3)
        println(data)       // Set(1,2) ???  排序了

        val map = mutable.Map.empty[String, String]
        map("java") = "yes"
        map("Scala") = "no"
        println(map)    // Map(java->yes, Scala->no

        val treeSet = TreeSet("Spark", "Scala", "Hadoop")
        println(treeSet)    // TreeSeet(Hadoop, Scala, Spark)  排序了

        val treeMap = TreeMap("java->yes", "Scala"->"no")
        println(treeMap)    // TreeMap(java->yes, Scala->no)
    }
}
