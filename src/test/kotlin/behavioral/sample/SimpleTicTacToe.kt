package behavioral.sample

import behavioral.All
import behavioral.Event
import behavioral.bProgram
import behavioral.bThread
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.runBlocking

sealed class TicTacToeEvent : Event {
    data class X(val row: Int, val col: Int) : TicTacToeEvent()
    data class O(val row: Int, val col: Int) : TicTacToeEvent()
    data object Draw : TicTacToeEvent()
    data object XWin : TicTacToeEvent()
    data object OWin : TicTacToeEvent()
}


class SimpleTicTacToe : FunSpec({

    test("TicTacToe") {
        for (time in 1..10) {
            runBlocking {
                playTicTacToe()
            }
        }
    }
})


fun playTicTacToe() {
    val winningLines = listOf(
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),
        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),
        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )

    val allXMove = setOf(
        TicTacToeEvent.X(0, 0), TicTacToeEvent.X(0, 1), TicTacToeEvent.X(0, 2),
        TicTacToeEvent.X(1, 0), TicTacToeEvent.X(1, 1), TicTacToeEvent.X(1, 2),
        TicTacToeEvent.X(2, 0), TicTacToeEvent.X(2, 1), TicTacToeEvent.X(2, 2)
    )

    val allOMove = setOf(
        TicTacToeEvent.O(0, 0), TicTacToeEvent.O(0, 1), TicTacToeEvent.O(0, 2),
        TicTacToeEvent.O(1, 0), TicTacToeEvent.O(1, 1), TicTacToeEvent.O(1, 2),
        TicTacToeEvent.O(2, 0), TicTacToeEvent.O(2, 1), TicTacToeEvent.O(2, 2)
    )

    val enforceTurns = bThread(name = "EnforceTurns") {
        while (true) {
            sync(
                waitFor = allXMove,
                blockEvent = allOMove
            )
            sync(
                waitFor = allOMove,
                blockEvent = allXMove
            )
        }
    }


    val detectDraw = bThread(name = "DetectDraw") {
        for (i in 1..9) {
            sync(waitFor = allXMove + allOMove)
        }
        sync(request = setOf(TicTacToeEvent.Draw))
    }

    val detectXWin = winningLines.map {
        val matched = it.map { cell ->
            TicTacToeEvent.X(cell / 3, cell % 3)
        }.toSet()

        bThread(name = "DetectXWin-$it") {
            sync(waitFor = matched)
            sync(waitFor = matched)
            sync(waitFor = matched)
            sync(request = setOf(TicTacToeEvent.XWin))
        }
    }.toTypedArray()

    // O 승리 감지 b-thread
    val detectOWin = winningLines.map {
        val matched = it.map { cell ->
            TicTacToeEvent.O(cell / 3, cell % 3)
        }.toSet()

        bThread(name = "DetectOWin-$it") {
            sync(waitFor = matched)
            sync(waitFor = matched)
            sync(waitFor = matched)
            sync(request = setOf(TicTacToeEvent.OWin))
        }
    }.toTypedArray()

    val gameEnd = bThread(name = "GameEnd") {
        sync(waitFor = setOf(TicTacToeEvent.OWin, TicTacToeEvent.XWin, TicTacToeEvent.Draw))
        sync(blockEvent = setOf(TicTacToeEvent.OWin, TicTacToeEvent.XWin, TicTacToeEvent.Draw))
    }


    val xStrategy = bThread(name = "XStrategy") {
        while (true) {
            val availableCells = listOf(
                Pair(1, 1),
                Pair(0, 0), Pair(0, 1), Pair(0, 2),
                Pair(1, 0), Pair(1, 2),
                Pair(2, 0), Pair(2, 1), Pair(2, 2)
            ).shuffled()

            sync(request = availableCells.map { TicTacToeEvent.X(it.first, it.second) }.toSet())
        }
    }

    val oStrategy = bThread(name = "OStrategy") {
        while (true) {
            val availableCells = listOf(
                Pair(1, 1),
                Pair(0, 0), Pair(0, 1), Pair(0, 2),
                Pair(1, 0), Pair(1, 2),
                Pair(2, 0), Pair(2, 1), Pair(2, 2)
            ).shuffled()

            sync(request = availableCells.map { TicTacToeEvent.O(it.first, it.second) }.toSet())
        }
    }


    val printBoard = bThread("PrintBoard") {
        val board = Array(3) { Array(3) { ' ' } }
        while (true) {
            sync(waitFor = All)
            if (lastEvent is TicTacToeEvent.Draw) {
                println("Draw!")
                break
            } else if (lastEvent is TicTacToeEvent.XWin) {
                println("X wins!")
                break
            } else if (lastEvent is TicTacToeEvent.OWin) {
                println("O wins!")
                break
            }


            if (lastEvent is TicTacToeEvent.X) {
                val e = lastEvent as TicTacToeEvent.X
                board[e.row][e.col] = 'X'
            } else if (lastEvent is TicTacToeEvent.O) {
                val e = lastEvent as TicTacToeEvent.O
                board[e.row][e.col] = 'O'
            }

            println("---------")
            for (i in 0..2) {
                println(board[i].joinToString(" | "))
            }
            println("---------")
        }
    }


    val disableSquareReuse = List(3) { i ->
        List(3) { j ->
            bThread("DisableSquareReuse($i, $j)") {
                while (true) {
                    sync(
                        waitFor = setOf(TicTacToeEvent.X(i, j), TicTacToeEvent.O(i, j))
                    )
                    sync(
                        blockEvent = setOf(TicTacToeEvent.X(i, j), TicTacToeEvent.O(i, j))
                    )
                }
            }
        }
    }.flatten().toTypedArray()

    val winnerAssert = bThread("WinnerAssert") {
        val board = Array(3) { Array(3) { ' ' } }

        fun checkWin(player: Char): Boolean {
            return winningLines.any { line ->
                line.all { cell ->
                    val row = cell / 3
                    val col = cell % 3
                    board[row][col] == player
                }
            }
        }

        while (true) {
            sync(waitFor = All)

            when (val event = lastEvent) {
                is TicTacToeEvent.X -> {
                    board[event.row][event.col] = 'X'
                }

                is TicTacToeEvent.O -> {
                    board[event.row][event.col] = 'O'
                }

                is TicTacToeEvent.XWin -> {
                    val isRealXWin = checkWin('X')
                    isRealXWin shouldBeEqual true
                }

                is TicTacToeEvent.OWin -> {
                    val isRealOWin = checkWin('O')
                    isRealOWin shouldBeEqual true
                }

                is TicTacToeEvent.Draw -> {
                    val isXWin = checkWin('X')
                    val isOWin = checkWin('O')
                    isXWin shouldBeEqual false
                    isOWin shouldBeEqual false
                }
            }
        }
    }


    val program = bProgram(
        oStrategy,
        xStrategy,

        winnerAssert,
        printBoard,

        enforceTurns,
        *disableSquareReuse,

        detectDraw,
        *detectOWin,
        *detectXWin,

        gameEnd,
    )

    //program.enableDebug()
    program.runAllBThreads()
}




