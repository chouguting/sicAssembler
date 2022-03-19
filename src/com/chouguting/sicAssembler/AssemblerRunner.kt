package com.chouguting.sicAssembler
import java.io.File
import java.io.FileWriter

fun main(args:Array<String>){
    val fileLoader = FileLoader(if (args.isEmpty()) "input.asm" else args[0])
    val inputLines = fileLoader.loadFileToLines()  //把檔案中的字讀入為string的list
    val assembler = Assembler(inputLines)
    val assembleResult = assembler.assemble()
    val fileWriter = FileWriter(if (args.size != 2) "result.obj" else args[1])
    assembleResult.forEachIndexed{
        index, assembledLine ->
        fileWriter.write(assembledLine)
        if(index!=assembleResult.lastIndex) fileWriter.write("\n")  //每行結尾加上換行符號(除了最後一行)
    }
    fileWriter.close()
}