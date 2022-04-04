package com.chouguting.sicXeAssembler

import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

class FileLoader(private val fileName: String) {
    fun loadFileToLines(): List<String> {
        lateinit var inputLines:List<String>
        try {
            inputLines = File(fileName).useLines { it.toList() }
        }catch (e: FileNotFoundException) {
            println("File not found" + e)
            println("找不到指定的檔案(如果未指定檔案預設是會讀入 input.asm)")
            println("請特別注意 txt檔要放在和jar檔同一個資料夾下")
            println("執行時要在資料夾外輸入指令 : java -jar sicAssembler.jar input.asm")
            exitProcess(-1)
        }
        return inputLines
    }
}