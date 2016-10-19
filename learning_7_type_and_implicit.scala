object Hello {
    def main(args: Array[String]): Unit = {
        test_structual_type
        test_infix_type
        test_self_type
        test_dependency_injection
        test_implicit_type_switch
        test_implicit_variable
        test_implicit_bound
        test_implicit_object
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_structual_type(): Unit = {
        print_banner("test structural type")

        def init(res: {def open():Unit}) {    // 结构类型，表示 res 可以是任何实现了 open():Unit 接口的类型，也即鸭子类型
            res.open
        }

        init(new {def open()=println("structural type opened")})   // 直接构建结构类型实例

        type X = {def open():Unit}             // 重命名结构类型为 X
        def init2(res: X) = res.open
        init2(new {def open()=println("structural type opened again")})

        object A {def open() {println("A single object opened")}}  // object 也可以
        init(A)

        class structural {def open()=println("A class instance opened")}   // 类实例也可以
        val s = new structural
        init(s)
    }

    def test_infix_type():Unit = {           // 中值表达式
        print_banner("test infix type")

        object Log {def >>:(data:String) : Log.type = {println(data); Log}}    // 定义 >>: 函数，返回 Log.type，为了链式调用
        "World" >>: "Hello" >>: Log    // 右结合，output Hello World

        case class Cons(f:String, s:String)
        val cs = Cons("first", "second")
        cs match {case "first" Cons "second" => println("matched infix expression")}
    }

    def test_self_type():Unit = {
        print_banner("test self type")

        class Self {
            self =>                   // 表示 self 是 this 的别名，this 是 scala 的关键字，表示自身实例指针
                val tmp = "Scala"     // 之前在内部类中见过，class Outer { outer => class Inner ... }  这里 outer 就是外部类 this 的别名
                def foo = self.tmp + this.tmp
        }

        trait S1
        class S2 {this:S1 => }        // this:S1 是在一起的，不能单拿出来，表示 S2 实例化时必须混入 S1 
        val s2 = new S2 with S1       // 必须 With S1
        class S3 extends S2 with S1   // S2 的子类也同样必须混入 S1
    }

    def test_dependency_injection():Unit = {
        print_banner("test dependency injection")

        trait Logger {def log (msg:String)}
        trait Auth {
            auth : Logger =>     // 见上面的函数，表示 auth 是 this 的别名，而且 Auth 实例化时必须混入 Logger
                def act(msg:String) {
                    log(msg)     // 既然一定要混入 Logger，故此一定可以调用 log
                }
        }
        object DI extends Auth with Logger {
            override def log(msg:String) = println(msg)
        }
        DI.act("dependency injection done")
    }

    def test_implicit_type_switch():Unit = {
        print_banner("test implicit type switch")
        
        import java.io.File
        import scala.io.Source
        class RichFile(val file:File) {
            def read = Source.fromFile(file.getPath()).mkString    // 定义 RichFile，通过 File 实例构建，提供 read 函数
        }
        object Context {
            implicit def file2RichFile(file:File) = new RichFile(file)    //  提供隐式类型转换函数，传入 File 返回 RichFile
        }
        import Context.file2RichFile    // 在函数的上下文中，引入上面定义的 Context.file2RichFile
        println(new File("implicit.txt").read)       // File 类没有 read 方法，这里会在上下文中查找 File 能够转换的且带有 read 函数的类型

        object ContextNew {
            implicit class FileEnhancer(file:File) { // 和上例的区别是，FileEnhancer 类本身就定义为 implicit 的，就不需要隐式的转换函数了
                def read2 = Source.fromFile(file.getPath()).mkString    // 改为 read2，避免和上面的 read 冲突
            }
        }
        import ContextNew._
        println(new File("implicit.txt").read2)
    }

    def test_implicit_variable():Unit = {
        print_banner("test implicit variable")

        object Context {
            implicit val sth:String = "default"
        }
        def pprint(str:String)(implicit impstr:String) {
            println("fix: " + str + "    implicit: " + impstr)
        }
        pprint("given")("also given")    // 即使是 implicit 参数，仍然可以直接指定
        import Context._
        pprint("given")                  // 在上下文中查找是否有 implicit 的 String 类型的变量，这样会在引入的 Context 中会找到 sth="default"
    }

    def test_implicit_bound():Unit = {
        print_banner("test implicit bound")

        def bigger[T](a:T, b:T)(implicit ordered: T => Ordered[T])    // 按定义，ordered 其实是个函数，输入 T，返回 Ordered[T] 类型
        = if (ordered(a) > b) a else b         // ordered(a) 把 T 转为 Ordered[T] 就可以使用 > 大于号了
        println(bigger(4, 3))                  // 4，这里使用 Ordered[Int]     
        println(bigger("Hello", "World"))      // World，这里使用 Ordered[String]，和 Ordered[Int] 一样都隐式定义在 scala 库 Ordered 中

        class P1[T:Ordering](val f:T, val s:T) {           // 上下文界定，就不需要显式使用 implicit curring 参数了
            def bigger(implicit ordered:Ordering[T]) =     // T:Ordering 表示存在 implicit Ordering[T] 类型的变量
                if (ordered.compare(f, s) > 0) f else s    // Ordering[T] 定义在 scala 库中，隐式定义了 Ordering[Int], Ordering[String] 等 trait，实现了 compare 方法
        }
        class P2[T:Ordering](f:T, val s:T) {
            def bigger = if (implicitly[Ordering[T]].compare(f, s) > 0)    // implicitly 是个函数，给定类型，生成该类型的隐式实例  
                f else s                                                   // def implicitly[T](implicit e:T) = e，故此 implicit[Ordering[T]] 其实是个函数调用，参数隐去了
        }
        class P3[T:Ordering](f:T, val s:T) {
            def bigger = {
                import Ordered._           // Ordered 中有 Ordering 到 Ordered 的隐式转换
                if (f > s) f else s        // 故此这里其实是 T => Ordering[T] => Ordered[T] 的转换，然后就可以使用大于号了
            }
        }
        println(new P1(7, 9).bigger)
        println(new P2(7, 9).bigger)
        println(new P3(7, 9).bigger)
    }

    def test_implicit_object():Unit = {
        print_banner("test implicit object")
    
        abstract class Template[T] {
            def add(x:T, y:T): T            // 抽象类的抽象方法
        }
        abstract class SubTemplate[T] extends Template[T] {
            def unit: T    // 同样，抽象类的抽象方法
        }
        implicit object StringAdd extends SubTemplate[String] {
            def add(x:String, y:String): String = x concat y
            def unit: String = ""
        }
        implicit object IntAdd extends SubTemplate[Int] {
            def add(x:Int, y:Int):Int = x + y
            def unit: Int = 0
        }

        def sum[T](xs:List[T])(implicit m: SubTemplate[T]): T = {
            if (xs.isEmpty) m.unit
            else m.add(xs.head, sum(xs.tail))
        }

        println(sum(List(1,2,3)))               // m 隐式转换为 IntAdd
        println(sum(List("a","b","c")))         // m 隐式转换为 StringAdd
    }
}
