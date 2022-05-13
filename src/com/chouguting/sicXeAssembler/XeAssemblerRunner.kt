package com.chouguting.sicXeAssembler

import java.io.FileWriter

fun main(args: Array<String>) {
    val fileLoader = FileLoader(if (args.isEmpty()) "inputXe.asm" else args[0])
    val inputLines = fileLoader.loadFileToLines()  //把檔案中的字讀入為string的list
    val xeAssembler = XeAssembler(inputLines)
    val assembleResult = xeAssembler.assemble()
    val fileWriter = FileWriter(if (args.size != 2) "resultXe.obj" else args[1])
    assembleResult.forEachIndexed { index, assembledLine ->
        fileWriter.write(assembledLine)
        if (index != assembleResult.lastIndex) fileWriter.write("\n")  //每行結尾加上換行符號(除了最後一行)
    }
    fileWriter.close()
}