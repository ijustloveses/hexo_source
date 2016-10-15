object Hello {
    def main(args: Array[String]): Unit = {
        test_generic
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_generic(): Unit = {
        print_banner("test generic")
        
        class Triple[F, S, T](val f:F, val s:S, val t:T)
        val triple = new Triple("Spark", 3, 3.14159)        // 这里进行了自动类型推导
        println(triple)
        val data = new Triple[String, Int, Char]("Scala", 3, 'o')
        println(data)
        
        def getData[T](list : List[T]) = list(list.length / 2)   // 返回传入列表的中间元素
        println(getData(List("A", "B", "C")))    // B
        def f = getData[Int] _            // 可以通过指定泛型f函数的类型得到偏函数
        println(f(List(1,2,3,4,5,6)))     // 4

        def foo[A](f: A=>List[A], b: A) = f(b)    // foo 的参数为一个函数 f 和一个值 b，函数 f 的参数和返回值为 A 和 List[A]
        println(foo(List.make(3, _), 5))    // List(5, 5, 5)
    }
}
