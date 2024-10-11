package behavioral


fun bThread(name: String, block: suspend RegisteredBThread.() -> Unit) = BThread(name, block)

fun bProgram(vararg bThread: BThread): BProgram {
    val program = BProgram()
    bThread.forEachIndexed { index, it ->
        program.registerBThread(it, index.toDouble())
    }
    return program
}
