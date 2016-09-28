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
        val arr = Array(2,4)
        // 两种遍历方式
        for(i <- 0 until arr.length) {
            println(arr(i))
        }
        for(elem <- arr) println(elem)
        // 定义数组
        val a = new Array[Int](3)  // 定长
        for(elem <- a) println(elem)  // 0, 0, 0
        val s = new Array[String](3) 
        for(elem <- s) println(elem)  // String 的初始化为 null, null, null，而不是 "" "" ""
        val t = new Array("Hello", "World") //定长 + 初始化
        t(0) = "Goodbye"    // 取元素 + 赋值
        val b = ArrayBuffer[Int]()  // 变长数组
        b += 1   // (1)
        b += (1,2,3)  // (1,1,2,3)
        b ++= Array(5,8)  // (1,1,2,3,5,8)
        b.trimEnd(2)   // 尾部去掉两个 (1,1,2,3)
        b.insert(2, 2) // 位置 2 (从 0 开始) 加入 2 (1,1,2,2,3)
        b.insert(2,7,8) // 位置 2 (从 0 开始) 加入 7,8 (1,1,7,8,2,2,3)
        b.remove(2)     // 去掉位置 2 (1,1,8,2,2,3)
        b.remove(2,2)   // 位置 2开始去掉两个 (1,1,2,3)
        b.toArray
        for(elem <- b) println(elem)
        // yield / filter / map /sum
        val c = Array(2,3,5,7)
        val res = for(elem <- c if elem % 2 == 0) yield 2 * elem
        for(elem <- res) println(elem)  // 4
        val res1 = c.filter(_ %2 == 0).map(2 * _)
        for(elem <- res1) println(elem)  // 4
        println(c.sum)  // 17
        // 排序
        val d = ArrayBuffer(1,7,2,9)
        val bSorted = d.sorted
        for(elem <- bSorted) println(elem)  // 1,2,7,9
        scala.util.Sorting.quickSort(d)    // sort in place
        for(elem <- d) println(elem)  // 1,2,7,9
        // mkString
        println(d.mkString(" and "))   // 1 and 2 and 7 and 9
        println(d.mkString("(", ",", ")"))  // (1,2,7,9)
        // 高维数组
        val mat = Array.ofDim[Double](2,3)
        mat(1)(2) = 42
        for(i <- 0 to 1; j <- 0 to 2) println(mat(i)(j))  //0.0 0.0 0.0 0.0 0.0 42
        val triangle = new Array[Array[Int]](3)
        for (i <- 0 until triangle.length) triangle(i) = new Array[Int](i + 1)
        for (i <- 0 until triangle.length) println(triangle(i).length)  // 1 2 3
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
