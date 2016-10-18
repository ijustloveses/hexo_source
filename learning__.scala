object Hello {
    def main(args: Array[String]): Unit = {
        test_structual_type
        test_infix_type
        test_self_type
        test_dependency_injection
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
}
