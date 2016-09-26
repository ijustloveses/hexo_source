object Hello {
    def main(args: Array[String]): Unit = {
        test_tuple
        test_array
        test_map
    }

    def test_tuple(): Unit = {
        val triple = (100, "Hello", "World")
        println(triple._1)
        println(triple._2)
    }

    def test_array(): Unit = {
        val arr = Array(2,4,6,8)
        for(i <- 0 until arr.length) {
            println(arr(i))
        }
        for(elem <- arr) println(elem)
    }

    def test_map(): Unit = {
        val ages = Map("Rocky" -> 27, "Ling" -> 5)
        for((k,v) <- ages) {
            println("Key is " + k + ", value is " + v)
        }
        for((k,_) <- ages) println("Key is " + k )
    }
}
