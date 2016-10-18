object Hello {
    def main(args: Array[String]): Unit = {
        test_generic
        test_type_bound
        test_view_bound
        test_context_bound
        test_class_tag
        test_multiple_bound
        test_type_constraint
        test_variance
        test_variance_site
        test_chain_invoke
        test_outer_path_dep
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

        class Pair[T : Ordering](val f: T, val s: T) {        // 这里 : 会在 context 中产生一个变量，是 Ordering[T] 类型的
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
        // 如果没有上面两个常量，那么会报错 could not find implicit value for evidence parameter of type M_A[Int]
    }

    def test_type_constraint():Unit = {
        print_banner("test type constraint")
        // A =:= B  // A 类型等同于 B 类型
        // A <:< B  // A 类型是 B 类型的子类
        def rocky[T](i:T)(implicit ev: T <:< java.io.Serializable) {    // 带一个 implicit 参数，指明 T 是 Serializable 的子类
            println("Type T is subclass of Serializable")
        }
        rocky("Spark")
    }

    def test_variance():Unit = {
        print_banner("test variance")
        /*
        如果T’是T一个子类，Container[T’]应该被看做是Container[T]的子类吗？
        协变covariant       C[T’]是 C[T] 的子类 [+T]
        逆变contravariant   C[T] 是 C[T’]的子类 [-T]
        不变invariant       C[T] 和 C[T’]无关   [T]
        */
        class Person
        class Student extends Person

        class C[+T](val args: T)
        val value:C[Person] = new C[Student](new Student)  // 看到 Student 是 Person 的子类，那么 C[Student] 也是 C[Person] 的子类

        class GParent
        class Parent extends GParent
        class Child extends Parent
        class CoVar[+A]
        class ContraVar[-A]
        def foo(x : CoVar[Parent]) : CoVar[Parent] = identity(x)
        def bar(x : ContraVar[Parent]) : ContraVar[Parent] = identity(x)

        foo(new CoVar[Child])    // OK, CoVar[Child] 是 CoVar[Parent] 的子类，可以被 CoVar[Parent] 类型的参数使用
        // foo(new CoVar[GParent])    // Fail，CoVar[Parent] 是 CoVar[GParent] 的子类，而子类 CoVar[Parent] 类型的参数，不能传递父类对象
        bar(new ContraVar[GParent]) // OK，同上
        // bar(new ContraVar[Child])  // Fail，同上
    }

    def test_variance_site():Unit = {
        print_banner("test variance site")
        
        class P[+T](val f:T, val s:T) {       // 这里可以用 P[T]，功能仍然相同；而不同就是 P 类是否支持协变
            def replaceFirst[R >: T](r : R) = new P[R](r, s)
        } 

        class P1[T](var f:T, var s:T) {              // 这里用的 var，而 P 类中不可以，否则会被认为是 contravariance
            def replaceFirst(r:T) = new P1[T](r, s)   // 这里可以直接使用 r:T，而不用使用基类，而 P 类中不可以
            def replaceFirst2[R >: T](r:R) = new P1[R](r, s)    // 同样可以使用基类来 replace first
        }
    }

    def test_chain_invoke():Unit = {
        print_banner("test chain invoke")
        
        class Animal {def breathe: this.type = {println("breathe"); this} }        // 返回 this.type 对象类型，Animal 或者其子类的类型
        class Cat extends Animal {def eat : this.type = {println("eat"); this} }
        val cat = new Cat
        cat.breathe.eat
    }
    
    def test_outer_path_dep():Unit = {
        print_banner("test outer path dependency")
        
        class Outer {
            private val x = 10
            class Inner {
                private val y = x + 10
            }
        }
        val outer = new Outer
        val inner = new outer.Inner   // 注意这里是 outer.Inner，不是 Outer.Inner，就是说内部类是依赖外部类对象，而不是外部类
        val inner2 : outer.Inner = new outer.Inner   

        val o1 = new Outer
        val o2 = new Outer
        // val i : o2.Inner = new o1.Inner    // Failed，不同外部类对象构成不同的路径，内部类依赖这个路径
        val i : Outer#Inner = new o1.Inner    // 这个是可以的，叫类型投影，Outer#Inner 是 o1.Inner 和 o2.Inner 共同的父类
        val i1 = new o1.Inner
        val i2 = new o2.Inner
        println(i1)                // Hello$Outer$1$Inner@5b480cf9，看到确实都是 Outer#Inner 的类型
        println(i2)                // Hello$Outer$1$Inner@6f496d9f
    }
}
