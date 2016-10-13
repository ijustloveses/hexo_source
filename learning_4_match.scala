object Hello {
    def main(args: Array[String]): Unit = {
        test_basic_match
        test_adv_match
        test_case_match
        test_adv_case_match
        test_option
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic_match(): Unit = {
        print_banner("test basic match")
        val data = 30
        data match {
            case 1 => println("First")
            case n if n == 2 => println("Second")     // 这里进行了一次变量赋值，传入的 data 赋值给 n
            case _ => println("Unknown")              // 看到，不需要 break，只匹配一次
        }

        val d2 = 2
        val result = d2 match {                       // match 也可以返回值
            case 1 => "The first"
            case 2 => "The second"
            case _ => "??"
        }
        println("2 match: " + result)
    }

    def test_adv_match(): Unit = {
        print_banner("test advanced match")

        def match_list(lst: Any) = lst match {          // Any 是 List 的父类
            case 0 :: Nil => println("List: 0")         // :: 是 List concat 符号，这里匹配只有一个元素且为 0
            case x :: y :: Nil => println("List: " + x + " " + y)     // 匹配两个元素，匹配后分别赋值给 x，y
            case 0 :: tail => println("List: 0 ....")   // 匹配首元素为 0，后面的元素赋值给 tail
            case _ => println("something else ...")
        }
        match_list(List(0))         // 匹配第一条
        match_list(List(1,2))       // 匹配第二条
        match_list(List(0,1,2))     // 匹配第三条
        match_list(List(1,2,3,4))  // 匹配第四条

        def match_array(arr: Any) = arr match {
            case Array(0) => println("Array: 0")
            case Array(x, y) => println("Array: " + x + " " + y)
            case Array(1, _*) => println("Array: 1 ...")         // _ 通配任何，* 表示 0 或多个
            case _ => println("something else ...")
        }
        match_array(Array(0))
        match_array(Array(2,1))
        match_array(Array(1))                // 匹配第三条
        match_array(Array(1,2,3))            // 匹配第三条
        match_array(Array(0,1,2,3))

        def match_tuple(tuple: Any) = tuple match {
            case (0, _) => println("Tuple: 0 ...")
            case (x, 0) => println("TUple: x 0")
            case _ => println("something else ...")
        }
        match_tuple((0, "Scala"))
        match_tuple((2, 0))
        match_tuple((1,2,0))            // 不能匹配第二个，就是说 x 只能代表一个元素
        match_tuple((0,1,2,3,4,5))      // 不能匹配第一个，就是说 _ 通配一个元素

        def match_type(t:Any) = t match {
            case p : Int => println("Integer: " + p)
            case p : String => println("String: " + p)
            case m : Map[_, _] => m.foreach(println)
            case _ => println("Unkown type")
        }
        match_type(2)
        match_type(Map("a" -> 1, "b" -> 2))
    }

    def test_case_match(): Unit = {
        print_banner("test case match")
        abstract class Person
        case class Student(age: Int) extends Person    // 看到这里 age 前面没有加 var/val，因为对于 case class 默认为 val
        case class Worker(age:Int, salary:Double) extends Person
        case object Shared extends Person

        def caseOps(person: Person) = person match {      // 看到接口编程，参数为抽象类的对象
            case Student(age) => println("I'm a " + age + " years old student")
            case Worker(_, salary) => println("Salary is " + salary)
            case Shared => println("case object instance")
        }
        caseOps(Student(19))                     // 内部实现了对应的 class object，及其 apply 函数，这里调用的就是 apply 函数
        caseOps(Shared)

        val wk = Worker(29, 10000)
        caseOps(wk)
        val wk1 = wk.copy(salary = 5000)             // 可以通过 copy 生成新对象，同时修改属性
        caseOps(wk1)
    }

    def test_adv_case_match():Unit = {
        print_banner("test adv case match")
        abstract class Item
        case class Book(desc: String, price: Double) extends Item
        case class Bundle(desc: String, price: Double, items: Item*) extends Item    // Bundle 后面可以匹配 0 到多个 Item 对象 (既可以是 Book, 也可能是 Bundle 嵌套)

        def caseOps(it: Item) = it match {
            case Bundle(_, _, _, Book(descr, _), _*) => println("2nd book desc is " + descr)    // 匹配至少有两个嵌套 Item，其第二个 Item 是 Book 的 Bundle，并输出第二个 Item 的描述
            case Bundle(_, _, bk @ Book(_, _), rest @ _*) => println("desc: " + bk.desc + "  price: " + bk.price)   // 匹配至少有一个嵌套 Item, 且第一个是 Book 的 Bundle，并把第一个 Item 赋值为 bk，后面的部分赋值为 rest
            case _ => println("Failed to match")
        }
        caseOps(Bundle("special", 30.0, Book("book 1", 14.0), Book("Book 2", 16.0)))    // 同时满足两个条件，被第一个 case 捕获
        caseOps(Book("just a book", 99.0))    // 不匹配
        caseOps(Bundle("special", 30.0, Book("book 1", 14.0), Bundle("bundle 2", 16.0, Book("book 2", 7.0), Book("book 3", 9.0))))  // 被第二个条件捕获
    }

    def test_option():Unit = {
        print_banner("test option")
        val scores = Map("Alice" -> 8, "Bob" -> 7)

        scores.get("Alice") match {
            case Some(s) => println("Score is : " + s)
            case None => println("No score")
        }
        scores.get("Bobster") match {
            case Some(s) => println("Score is : " + s)
            case None => println("No score")
        }
    }
}
