object Hello {
    def main(args: Array[String]): Unit = {
        test_basic
        test_sort_by_match
        test_merge_sort
        test_orderone_funcs
        test_adv_funcs
        test_obj_funcs
    }

    def print_banner(msg: String): Unit = {
        println("---------- " + msg + " ----------")
    }

    def test_basic(): Unit = {
        print_banner("test basic")
