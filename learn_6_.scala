object Hello {
    def main(args: Array[String]): Unit = {
        test_generic
        test_type_bound
        test_view_bound
        test_context_bound
        test_class_tag
        test_multiple_bound
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_generic(): Unit = {
        print_banner("test generic")
        
        // 类级别泛型
        class Triple[F, S, T](val f:F, val s:S, val t:T)
        val triple = new Triple("Spark", 3, 3.14159)        // 这里进行了自动类型推导
        println(triple)        // 类似 Hello$Triple$1@5f184fc6，表示对象实例
        val data = new Triple[String, Int, Char]("Scala", 3, 'o')
        println(data)          // 类似 Hello$Triple$1@3feba861
        
        // 函数级别泛型
        def getData[T](list : List[T]) = list(list.length / 2)   // 返回传入列表的中间元素
        println(getData(List("A", "B", "C")))    // B
        def f = getData[Int] _            // 可以通过指定泛型f函数的类型得到具体类型的函数，类似偏函数
        println(f(List(1,2,3,4,5,6)))     // 4

        def foo[A](f: A=>List[A], b: A) = f(b)    // foo 的参数为一个函数 f 和一个值 b，函数 f 的参数和返回值为 A 和 List[A]
        println(foo((x:Int)=>List.fill(3)(x), 5))    // List(5, 5, 5)
    }

    def test_type_bound():Unit = {
        print_banner("test type bound")

        class Pair[T <: Comparable[T]](val f: T, val s: T) {    // 这里 <: 要求类型 T 继承自 Comparable
            def bigger = if(f.compareTo(s) > 0) f else s        // 这样，这里才能使用 compareTo
        }
        // 上面是类级别，这里是函数级别
        class Pair_LB[T](val f:T, val s:T) {
            // 这里要求类型 R 是类型 T 的父类，这样可以替换掉原 Pair 的第一个元素，并把第二个元素向父类型推导，得到父类型的新 Pair
            def replaceFirst[R >: T](newf: R) = new Pair_LB[R](newf, s)
        }

        val pair = new Pair("Spark", "Hadoop")   // 没有指定 T，自动类型推导
        println("Bigger one between Spark and Hadoop is " + pair.bigger)

        import scala.runtime.RichInt
        val pairlb = new Pair_LB[RichInt](new RichInt(5), new RichInt(3))
        println("replace pair first of (RichInt(5), RichInt(3)) to 1, get: (" + pairlb.replaceFirst(1).f + ", " + pairlb.replaceFirst(1).s + ")")        // (1, 3)
    }

    def test_view_bound():Unit = {
        print_banner("test view bound")

        class PairBad[T <% Comparable[T]](val f: T, val s: T) {    // 这里 <% 要求类型 T 能隐式转换成继承自 Comparable 的类型
            def bigger = if(f.compareTo(s) > 0) f else s
        }
        val pair = new PairBad(3, 5)   // int 并不继承自 Comparable，但是可以自动转换为 RichInt，就继承自 Comparable 了
        println("Bigger one between 3 and 5 is " + pair.bigger)

        class PairBetter[T <% Ordered[T]](val f: T, val s: T) {    // 这里 <% 要求类型 T 能隐式转换成继承自 Ordered 的类型
            def bigger = if(f > s) f else s                        // Ordered 类型可以使用大于号了，方便很多
        }
        val pair2 = new PairBetter(3, 5)
        println("Bigger one between 3 and 5 is " + pair2.bigger)
    }

    def test_context_bound():Unit = {
        print_banner("test context bound")

        class Pair[T : Ordering](val f: T, val s: T) {        // 这里 : 会在 context 中产生一个变量，是 Ordering 类型的
            def bigger(implicit ordered: Ordering[T]) = {     // 看到，context 自动生成的变量就是 implicit ordered
                if(ordered.compare(f, s) > 0) f else s
            }
        }
        val pair = new Pair(3, 5)
        println("Bigger one between 3 and 5 is " + pair.bigger)
    }

    def test_class_tag():Unit = {
        print_banner("test class tag related")

        def arrMaker[T : Manifest](f:T, s:T) = {    // 看到这里是上面 context bound 的语法，其实会 implicitly 创建一个 Manifest[T] 类型
            val r = new Array[T](2)    // 如果没有 T:Manifest，这里编译时会报错 cannot find class tag for element type T
            r(0)=f; r(1)=s; r          // 原因是虽然运行期可以通过类型推导得知传入元素的类型，但是在编译期，scala 无法知道泛型的类型，故此通过 context bound 生成一个 Manifest[T] 对象用于存储类型信息
        }
        println("arr maker (1, 2), get:")
        arrMaker(1,2).foreach(println)

        // 如果不用上面的写法，那么可以麻烦一些，直接定义这个 Manifest 对象
        def checkType[T](x:List[T])(implicit m: Manifest[T]) = {
            if (m <:< manifest[String])              // 后面测试用例会讨论
                println("List of Strings")
            else
                println("Some other types")
        }
        checkType(List("Spark", "Hadoop"))    // List of Strings；由于是 implicit，故此不需要显示调用 m
        checkType(List(1,2))    // Some other types

        // 具体看看 manifest & classManifest，前者更强，后者稍弱
        class A[T]
        val m = manifest[A[String]]
        println(m)                          // Hello$A$1[java.lang.String]
        val cm = classManifest[A[String]]
        println(cm)                         // Hello$A$1[java.lang.String]

        // 新版本中，manifest => TypeTag，classManifest => ClassTag
        import scala.reflect.ClassTag
        def mkArr[T : ClassTag](elems: T*) = Array[T](elems: _*)
        println("mkArr(42, 13) get:")
        mkArr(42, 13).foreach(println)
        println("mkArr(Ab, C, De) get:")
        mkArr("Ab", "C", "De").foreach(println)
    }

    def test_multiple_bound():Unit = {
        print_banner("test multiple bound")
        class M_A[T]
        class M_B[T]
        implicit val a = new M_A[Int]
        implicit val b = new M_B[Int]
        
        def foo[T : M_A : M_B](i:T) = println("Int : M_A : M_B is OK")
        foo(2)      // 前面 implicit 定义的两个常量使得 Int : M_A : M_B 可以成功
    }
}
