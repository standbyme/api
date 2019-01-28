package standbyme.ReedSolomon

import org.junit.Test

val TestCase1 = (0..5)
val TestCase2 = arrayOf(Pair(0, 1),
        Pair(0, 2),
        Pair(0, 3),
        Pair(0, 4),
        Pair(0, 5),
        Pair(1, 2),
        Pair(1, 3),
        Pair(1, 4),
        Pair(1, 5),
        Pair(2, 3),
        Pair(2, 4),
        Pair(2, 5),
        Pair(3, 4),
        Pair(3, 5),
        Pair(4, 5))

class MainTests {
    @Test
    fun main() {
        arrayOf("", "1", "ha", "hah", "ha ha,", "ha \n123dkjiow9032453425 fd';[;]    ").forEach { raw ->
            val byteArray = raw.toByteArray()
            val fileSize = byteArray.size

            val encodeResult = encode(byteArray)
            val shards = encodeResult.shards as Array<Shard?>
            val shardSize = encodeResult.shardSize

            println(raw)

            TestCase1.forEach {
                println("""Remove $it""")

                shards[it] = null

                assert(shards[it] == null)

                val recoverResult = recover(shards, shardSize)

                assert(shards[it] != null)

                val shardsAfterRecover = recoverResult.shards

                val decodeByteArray = decode(shardsAfterRecover, fileSize)

                assert(byteArray.contentEquals(decodeByteArray))
            }

            TestCase2.forEach {
                println("""Remove ${it.first} ${it.second}""")

                shards[it.first] = null
                shards[it.second] = null


                val recoverResult = recover(shards, shardSize)

                assert(shards[it.first] != null)
                assert(shards[it.second] != null)

                val shardsAfterRecover = recoverResult.shards

                val decodeByteArray = decode(shardsAfterRecover, fileSize)

                assert(byteArray.contentEquals(decodeByteArray))
            }
        }
    }
}