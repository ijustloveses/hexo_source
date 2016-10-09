class Basic {                // 这里由于默认主构造器没有参数，故此省略了括号
    private var age = 0
    def incr() {age += 1}    // 这里没有 = 等于号
    def decr() = {age -= 1}
    def cur = age            // 这里省略了括号
}

class GetterSetter {
    var age = 0              // 自动生成 Getter & Setter
    val gender = 'F'         // 自动生成 Getter，由于不可变，故此不生成 Setter
    private var name: String = _     // 不会自动生成 Getter & Setter；另，name 初始化为空，注意这里必须要指定类型，否则无法绑定类型；最后，只有 var 可以使用占位符 _ 来初始化
    def pubname = name       // getter
    def pubname_= (value: String) = name = value       // setter，注意这里 _= 一定要写在一起，不能分隔空格; 就是说，pubname_= 合在一起是函数名
}

class PrivateAttr {
    private var privateAge = 0            // 类级别的私有属性
    def age = privateAge                  // getter
    def age_= (value: Int) = privateAge = value  // setter

    private[this] var privateWeight = 0   // 私有属性，不仅是类私有，而且是实例私有，不能被同类的其他实例访问
    def weight = privateWeight
    def weight_= (value: Int) = privateWeight = value

    def isYounger(other: PrivateAttr) = privateAge < other.privateAge
    // def isThinner(other: PrivateAttr) = privateWeight < other.privateWeight  这个会失败，因为是私有属性 private[this]
}

class Constructor {           // 默认的主构造器没有参数，省略了括号
    var name : String = _
    private var age = 27
    private[this] val gender = "male"

    def this(name: String) {
        this                  // 调用主构造器，重载的构造器都间接或直接的调用默认主构造器
        this.name = name
    }

    def say() {
        println(this.name + ":" + this.age + ":" + this.gender)
    }
}

class PrivateConstructor private (val name: String, val age: Int) {    // 参数前加 var 或 val，就表示参数为类成员变量；private 表示主构造器不能外部调用
    println("This is primary constructor")
    var gender : String = _

    def this(name: String, age: Int, gender: String) {
        this(name, age)
        this.gender = gender
    }

    def say() {
        println(this.name + ":" + this.age + ":" + this.gender)
    }
}

class Outer(val name: String) { outer =>    // 定义 Inner 时，自动生成 Inner 所属的 Outer 实例 outer
    class Inner(val name: String) {
        def foo(b:Inner) = println("Outer: " + outer.name +    // Inner 类中可以直接访问所属的 Outer 实例 outer
            "  Inner: " + b.name)
    }
}

object Singleton {         // object 相当于静态类，不需要实例化，也可以理解为单例
    private var cnt = 0
    def incr = {           // public 的；如果前面加上 private，外界不能调用
        cnt += 1
        cnt
    }
    private def private_incr = {    // private 的，可以被伴生类调用
        cnt += 1
        cnt
    }
}

class Singleton {          // object Singleton 的伴生类
    val cur_cnt = Singleton.private_incr    // 可以访问 private 成员
}

class ObjApply {
    def foo {
        println("Have a try on object-level apply")
    }
}

object ObjApply {
    // apply 函数，就是提供主体被调用的功能
    def apply() = {        // obj 级的 apply，直接调用 object 主体，即 ObjApply()；类似 Array(1,2,3)
        new ObjApply       // 调用后，生成一个 ObbjApply() 类的实例
    }
}

class ClassApply {
    // class 级的 apply，需要创建实例，然后把实例当作函数调用
    def apply() = println("Have a try on class-level apply")
}

class Parent(val name: String, var age: Int) {    // 默认继承自 Object；name & age 是欲定义的成员变量，故此前面加上了 var/val
    println("primary constructor of Parent")
    val school = "BJU"
    def sleep = "8 hours"
    override def toString = "I am a parent"       // 重写 Object 的 toString 函数
}

class Child(name: String, age: Int, val gender: String) extends Parent(name, age) {     // name & age 是继承来的，故此不用加 val/var 了
    println("primary constructor of Child")
    override val school = "TSU"                   // 重写字段
    override def toString = "I am a child, sleep " + super.sleep           // 重写函数，并通过 super 调用父类函数
}

abstract class Abstract(val name: String) {     // 抽象类需要加 abstract 关键字，也可以有带参数的默认构造器
    var id: Int                        // 抽象类成员变量不能初始化
    def printmsg                       // 抽象类成员函数同样不能定义
}

class Concrete(name: String) extends Abstract(name) {    // 继承抽象类的构造器
    override var id = name.hashCode()                    // 重写成员函数和成员变量，抽象类的子类 override 可以不写，当然写了更清楚
    override def printmsg {
        println("id: " + id)   
    }
}

// trait 类似于抽象类，也可以提供接口功能；一个 scala 类只能继承一个类，但是可以 with 多个 trait，类似多重继承
trait Logger {
    def log (msg: String)       // 抽象方法
    def show {}    // trait 中可以有具体方法，但是什么都没做
}

class CLogger extends Logger with Cloneable {                 // Cloneable 也是一个 trait，scala 自带的
    def log(msg: String) = println("Log: " + msg)             // 抽象方法的重写，可以不带 override
    def clog {
        log("It's me")
    }
}

trait TraitLogger extends Logger {         // 注意，TraitLogger 甚至没有实现 Logger 的抽象成员函数，只重写了一个具体函数
    override def show = println("override concrete method in trait")   // 具体方法的重写需要加 override
}

class Human {
    println("Human")
}

trait Teacher extends Human {
    println("Teather")
    def teach                     // 抽象方法
}

trait PianoPlayer extends Human {
    println("PianoPlayer")
    def play = {println("I'm playing piano")}    // 具体方法
}

class PianoTeacher extends Human with Teacher with PianoPlayer {      // 构造函数调用顺序 左 --> 右
    override def teach = {println("I'm teaching")}
}

trait Action {
    def doAction                // trait 定义一个抽象方法
}

trait AopAction extends Action {
    abstract override def doAction {    // 这里声明此重写的 do 函数仍然是抽象的
        println("Init ...")
        super.doAction                 // 之所以是抽象的，就是因为这里调用 super.do，这个 super 是抽象的
        println("Done")
    }
}

class RealAction extends Action {
    override def doAction = println("Really do something")
}

object Hello {
    def main(args: Array[String]): Unit = {
        test_basic
        test_getter_setter
        test_private_attr
        test_constructor
        test_private_constructor
        test_outer_inner
        test_singleton
        test_friend_class
        test_obj_apply
        test_class_apply
        test_child
        test_abstract
        test_trait
        test_multiple_inherit
        test_aop
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic(): Unit = {
        print_banner("test basic")
        val b = new Basic()
        b.incr()
        b.incr()
        b.decr()
        println("current age is " + b.cur)     // 这里调用也没有使用括号
    }

    def test_getter_setter(): Unit = {
        print_banner("test getter setter")
        var gs = new GetterSetter()
        gs.age = 25
        println("age is " + gs.age)
        // gs.gender = "M"                     // 这句会报错，val 类型不能 set
        println("gender is " + gs.gender)
        gs.pubname = "John"                    // pubname 既可以 set 又可以 get
        println("name is " + gs.pubname)
    }

    def test_private_attr(): Unit = {
        print_banner("test private attr")
        var pa1 = new PrivateAttr()
        var pa2 = new PrivateAttr()
        pa1.age = 5
        pa2.age = 27
        println(if (pa1.isYounger(pa2)) "yes, younger" else "no, older")   // output "yes, younger"
    }

    def test_constructor(): Unit = {
        print_banner("test constructor")
        var cons1 = new Constructor()
        cons1.name = "John"
        var cons2 = new Constructor("Leon")
        cons1.say
        cons2.say
    }

    def test_private_constructor(): Unit = {
        print_banner("test private constructor")
        // val con = new PrivateConstructor("John", 25)   这个会报错，因为是 private 的
        val con = new PrivateConstructor("John", 25, "male")
        con.say
    }

    def test_outer_inner(): Unit = {
        print_banner("test outer inner")
        val o1 = new Outer("O1")
        val o2 = new Outer("O2")
        val i1 = new o1.Inner("I1")      // 注意看内部类初始化时，是要指定其所属外部类实例的
        val i2 = new o2.Inner("I2")
        i1.foo(i1)   // Outer: O1  Inner: I1
        i2.foo(i2)   // Outer: O2  Inner: I2
        // i1.foo(i2)  这个会失败，因为 i1 所属的 Outer 实例是 o1
    }

    def test_singleton(): Unit = {
        print_banner("test singleton")
        println("1st incr: " + Singleton.incr)      // output 1，直接使用静态类名，不需要实例化
        println("2nd incr: " + Singleton.incr)      // output 2
    }

    def test_friend_class(): Unit = {
        print_banner("test friend class")
        val fc = new Singleton()
        println("cnt after incr: " + fc.cur_cnt)    // output 3，仍然是访问静态类的静态成员 cnt
    }

    def test_obj_apply(): Unit = {
        print_banner("test object apply")
        val a = ObjApply()         // 把 object 名字当作函数来调用，内部调用 apply
        a.foo
    }

    def test_class_apply(): Unit = {
        print_banner("test class apply")
        val a = new ClassApply()
        a()                        // 把类的实例当作函数来调用，内部调用 apply
    }

    def test_child(): Unit = {
        print_banner("test child")
        val c = new Child("Junior", 5, "M")    // 这里会打印子类和父类构造函数中的消息
        println("School: " + c.school)
        println("Gender: " + c.gender)
        println(c.toString)
    }

    def test_abstract(): Unit = {
        print_banner("test abstract")
        val conc = new Concrete("extends_from_abstract")
        conc.printmsg
    }

    def test_trait(): Unit = {
        print_banner("test trait")
        val cl = new CLogger with TraitLogger
        cl.clog                    // output: "Log: It's me"
        cl.show                    // output: "override concrete method in trait"
    }
    
    def test_multiple_inherit(): Unit = {
        print_banner("test multiple inherit")
        val t = new PianoTeacher
        t.play
        t.teach
        val t2 = new Human with Teacher with PianoPlayer {
            def teach = println("I'm teaching")
        }
        t2.play
        t2.teach
    }

    def test_aop(): Unit = {
        print_banner("test aop")
        val ra = new RealAction with AopAction           // 这里，super.do 不再抽象，而是变成了 RealAction 重写的 do 函数
        ra.doAction                                      // 于是，AopAction trait 混入 RealAction 的运行，实现了 Aop
    }
}
