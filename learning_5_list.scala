object Hello {
    def main(args: Array[String]): Unit = {
        test_basic
        test_sort_by_match
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic(): Unit = {
        print_banner("test basic")

        val l1 = "Hello"::("World"::Nil)    // 右->左匹配
        val l2 = "Hello"::"World"::Nil      // 和上面一样
        println(l1)
        println(l2)
        
        // 简易匹配赋值
        val List(a, b) = l1
        println("1st: " + a + "  2nd: " + b)
        val head :: rest = l2
        println("1st: " + head)
    }

    def test_sort_by_match(): Unit = {
        print_banner("test sort list by match")
        val old = List(6,2,5,7,1)
        println(sortlist(old))

        // 对 lst 排序
        def sortlist(lst : List[Int]): List[Int] = lst match {      // List[Int] 定义元素为 Int 的 List 类型
            case List() => List()
            case head :: rest => compute(head, sortlist(rest))      // 如果非空，那么递归排序尾部，并对头部和排序后的尾部进行位置调整
        }
    
        // 把元素 it 插入到排好序的 sortedlist 的恰当位置
        def compute(it: Int, sortedlist: List[Int]): List[Int] = sortedlist match {
            case List() => List(it)
            case head :: rest => if (it <= head) it :: sortedlist   // 如果非空，且头部大于待插入元素 it，那么把 it 摆在第一个位置上 
                else head :: compute(it, rest)                      // 否则，头部保持不变，并递归调用 compute，把元素 it 插入尾部的恰当位置
        }
    }
}
