object Hello {
    def main(args: Array[String]): Unit = {
        test_tuple
        test_array
        test_map
        test_for
        test_lambda
        test_inline
        test_multi_args
    }

    def print_banner(msg: String): Unit = {
        println("----------" + msg + "----------")
    }
    
    def test_tuple(): Unit = {
        print_banner("testing tuple ...")
        val triple = (100, "Hello", "World")
        println(triple._1)  // 100
        println(triple._2)  // Hello
    }

    def test_array(): Unit = {
        print_banner("testing array ...")
        val arr = Array(2,4,6,8)
        for(i <- 0 until arr.length) {
            println(arr(i))
        }
        for(elem <- arr) println(elem)
    }

    def test_map(): Unit = {
        print_banner("testing map ...")
        val ages = Map("Rocky" -> 27, "Ling" -> 5)
        for((k,v) <- ages) {
            println("Key is " + k + ", value is " + v)
        }
        for((k,_) <- ages) println("Key is " + k )
    }

    def test_for(): Unit = {
        print_banner("testing for ...")
        for(i <- 1 to 2; j <- 1 to 2) println("i: " + i + "   j: " + j)   // (1,1),(1,2),(2,1),(2,2)
        for(i <- 1 until 3; j <- 1 until 3 if i != j) println("i: " + i + "   j: " + j)   // (1,2),(2,1)
    }

    def test_lambda(): Unit = {
        print_banner("testing lambda ...")
        val add100 = (x : Int) => x + 100
        val x = 1
        println("x = " + x + " and  x + 100 = " + add100(x))
    }

    def test_inline(): Unit = {
        print_banner("testing inline ...")
        // 必须指定返回值，否则递归时不知道上一次结果的类型
        def fac(n:Int):Int = if (n <= 0) 1 else n * fac(n - 1)
        println("fac of 5 is " + fac(5))
    }    

    def test_multi_args(): Unit = {
        print_banner("testing multiple args ...")
        def concat(args:String*) = {
            var res = ""
            for(arg <- args) res += arg
            res
        }
        println("result of concat(a, b, c) is " + concat("a", "b", "c"))
    }
}
