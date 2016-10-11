import java.lang._                         // import java.lang 中的全部包
import java.awt.{Color, Font}              // import java.awt 中的 Color & Font 两个包
import java.util.{HashMap=>JavaHashMap}    // 为了避免 java 和 scala 中的同名包冲突，可以引入后改名；类似 python 的 import xx as yy
import scala.{StringBuilder => _}          // 不引入 scala.StringBuilder，这样使用的就是 java 中的 StringBuilder 了

package object PkgObj {
    val name = "Package Object"
}

package PkgObj {
    class PkgObjCls {
        def show: Unit = {
            println("Package Object Name: " + name)
        }
    }
}

import scala.io.Source
import java.io.{PrintWriter, File}

object Hello {
    def main(args: Array[String]): Unit = {
        test_package_object
        test_io
        test_regexp
        test_inner_func
        test_lambda
        test_partial
        test_closure
        test_highlvl_func
        test_curring
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_package_object(): Unit = {
        print_banner("test package object")
        val b = new PkgObj.PkgObjCls()
        b.show
    }

    def test_io():Unit = {
        print_banner("test io")
        val writer = new PrintWriter(new File("test_io.log"))
        for (i <- 1 to 5) writer.println(i)
        writer.close

        val file = Source.fromFile("test_io.log")
        file.foreach(print)         // 也可以使用 for 来遍历 file.getLines 生成的迭代器
        file.close

        print("Please enter your input: ")
        // val line = Console.readLine      // 这个是 deprecation 的版本，已经不用了
        val line = scala.io.StdIn.readLine
        println("Thanks, and you just entered: " + line)
    }

    def test_regexp():Unit = {
        print_banner("test regexp")

        val numberPattern = """\s+[0-9]+\s+""".r      // 用三个引号，那么引号中的表达式就不需要转义了，所见即所得
        for(matchStr <- numberPattern.findAllIn("99345 scala, 22298 spark")) println(matchStr)
        println(numberPattern.findFirstIn("99 java, 222 hadoop"))     // 这里返回 Some( 222 )，是一个 Option

        val numitemPattern = """([0-9]+) ([a-z]+)""".r
        val line = "99 hadoop"
        val numitemPattern(num, item) = line          // line 必须整体严格匹配正则表达式，包含正则表达式是不行的，会报错
        println("num: " + num + "  item: " + item)
        line match {                                   // 模式匹配
            case numitemPattern(num, item) => println("num: " + num + "  item: " + item)
            case _ => println("not found")
        }
    }

    def test_inner_func(): Unit = {
        print_banner("test inner function")
        def processLine(line: String) {
            if (line.length > 5)
                println("LongLine: " + line)
        }
        val lines = Array("abcdefg", "hijklmn", "opq", "rst")
        lines.foreach(processLine)
    }

    def test_lambda():Unit = {
        print_banner("test lambda")
        val numbers = List(-5, 1, 5)
        println("list numbers are: ")
        numbers.foreach((x: Int) => println(x))
        numbers.filter((x: Int) => x > 0)
        numbers.filter((x) => x > 0)
        numbers.filter(x => x > 0)
        numbers.filter(_ > 0)
        println("list numbers which greater than 0 are: ")
        numbers.filter(_ > 0).foreach(println)       
        val f = (_:Int) + (_:Int)
        println("(_:Int) + (_:Int) with 5 & 10 gives " + f(5, 10))
    }

    def test_partial():Unit = {
        print_banner("test partial")
        def sum(a:Int, b:Int, c:Int) = a + b + c

        val fp_a = sum _    // 这里，sum _ 会生成一个类，并实现 apply 方法，调用前面的 sum 函数
        println("sum of 1, 2 and 3 is " + fp_a(1, 2, 3))

        val fp_b = sum(1, _:Int, 3)
        println("sum of 1, 3 and a given number are:")
        println("given 2, get " + fp_b(2))
        println("given 10, get " + fp_b(10))
    }

    def test_closure():Unit = {
        print_banner("test closure")

        var sum = 0
        val l = List(1,2,3,4,5)
        l.foreach(sum += _)           // sum += _ 这个匿名函数，使用了作用于之外的变量 sum
        println("sum 1 to 5 is: " + sum)

        def add(more: Int) = (x: Int) => x + more      // 定义一个匿名函数，使用外部传入的参数 more
        val a = add(10)
        println("10 add by 99 gives us " + a(99))
    }

    def test_highlvl_func():Unit = {
        print_banner("test high level functions")
        println("multiple 1 to 9 is: " + (1 to 9).reduceLeft(_ * _))      // 362880
        println("Today is a good day to die".split(" ").sortWith(_.length < _.length).mkString(" "))     // sort by length
        
        def box(f: (Double) => Double) = f(0.25)             // box 的参数是一个函数，输入和返回都是 Double，box 运行时使用 0.25 调用函数
        println("triple of 0.25 is " + box(_ * 3))
        println("0.25 add to 1 is " + box(x => x + 1.0))
    }

    def test_curring():Unit = {
        print_banner("test curring")
        def multipleOne(x:Int) = (y:Int) => x * y
        println("multiple 6 with 7 is " + multipleOne(6)(7))

        def curring_multi(x:Int)(y:Int) = x * y
        println("multiple 6 with 7 is " + curring_multi(6)(7))
    }
}
