import scala.actors.Actor

object Hello {
    def main(args: Array[String]): Unit = {
        test_actor_basis
        test_actor_more 
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_actor_basis(): Unit = {
        print_banner("test actor basis")

        object FA extends Actor {
            def act() {
                for (i <- 1 to 5) {
                    println("Step: " + i)
                    println(Thread.currentThread().getName)    // like ForkJoinPool-1-worker-11
                    Thread.sleep(1000)
                }
            }
        }
        object SA extends Actor {
            def act() {
                for (i <- 1 to 5) {
                    println("Step Further: " + i)
                    println(Thread.currentThread().getName)
                    Thread.sleep(1000)
                }
            }
        }
        FA.start
        SA.start    // FA 和 SA 将是同时进行的，而不是按先后顺序执行；原因是在不同的 threads 中运行的
    }

    def test_actor_more():Unit = {
        print_banner("test more actor")

        val aMsg = actor {       // anonymous actor
            while(true) {
                receive {        // receive 是一个偏函数
                    case msg => println("msg recv: " + msg)
                }
            }
        }
        val dMsg = actor {
            while(true) {
                receive {
                    case msg:Double => println("double msg recv: " + msg)
                }
            }
        }
        aMsg ! "Spark"
        dMsg ! Math.PI
        dMsg ! "Hadoop"    // 这条会被忽略掉

        case class Person(name:String, age:Int)
        object MA extends Actor {   // named actor
            def act() {
                while(true) {
                    receive {
                        case msg:String => println("named actor string msg recv: " + msg)
                        case Person(name, age) => println("name actor person recv with name: " + name + " and age: " + age)
                        case _ => println("named actor recv other msg: " + msg)
                    }
                    sender ! "Got!"   // 给发信者回信
                }
            }
        }
        MA.start
        MA ! "Named Actor"
        MA ! Math.PI
        MA ! Person("JohnDoe", 28)    // 发送 case class

        self.receiveWithin(3000) {case msg => println("MA got msg and replied: " + msg)}    // 原生线程作为 Actor 接受消息，且 3 秒后自动结束
    }

    def test_react_loop():Unit = {
        print_banner("test react loop")

        case class Net(name:String, actor:Actor)

        object NameResolver extends Actor {
            def act() {
                react {  // 和 receive 一样是偏函数，一样是一次性的 (故此 receive 都放在 while true 里)；不同的是 react 不返回，故此可以重用线程堆栈
                    case Net(name, actor) => 
                        actor ! "NameResolver Got " + name
                        act                      // 这里重用线程，再次 react 等待消息
                    case "Exit" => println("Exiting...")
                    case msg => 
                        println("Unhandled msg: " + msg)
                        act
                }
            }
        }
        object NR extends Actor {
            def act() {
                loop {    // react 还可以和 loop 一起调用，类似 while true 和 receive 一起调用
                    react {
                        case Net(name, actor) =>
                            actor ! "NR Got " + name
                        case msg => 
                            println("Unhandled msg: " + msg)
                    }
                }
            }
        }
        
        NameResolver.start
        NR.start
        NameResolver ! Net("www.baidu.com", self)
        NR ! Net("google.com", self)

        println(self.receiveWithin(1000){case x => x})
    }
}
