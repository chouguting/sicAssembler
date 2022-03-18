package com.chouguting.sicAssembler
import java.io.FileWriter

fun main(args:Array<String>){
    val fileLoader = FileLoader(if (args.isEmpty()) "input.asm" else args[0])
    val inputLines = fileLoader.loadFileToLines()  //把檔案中的字讀入為string的list
    val assembler = Assembler(inputLines)
    val assembleResult = assembler.assemble()
    val fileWriter = FileWriter("result.txt")
    fileWriter.write(assembleResult)
    fileWriter.close()
}