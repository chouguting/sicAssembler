package com.chouguting.sicAssembler

import kotlin.system.exitProcess

const val PSEUDO = -1    // Pseudo opcode
const val WORD_SIZE = 3  //SIC中一個word的長度是3個byte

class Assembler(var inputLines: List<String> = listOf()) {
    //把單行字串轉成指令
    private fun String.toInstruction(): InstructionLine? {
        if (this.isEmpty()) return null  //如果是空字串 就不處理

        if (this.trim()[0] == '.' || this.trim()[0] == '\n') return null //如果是註解或換行 就不處理
        val splittedInstruction = this.uppercase().trim().split(" ", "\t") //將字串切割成不同部分(LABEL,OPCODE,OPERAND，雖然不一定都有)
        when (splittedInstruction.size) {
            //如果只有切出一段字串 代表一定是只有一個指令
            1 -> if (splittedInstruction[0].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), null)
            }
            //如果是切出兩段字串 有可能是(Label+指令) 或是 (指令+operand)
            2 -> if (splittedInstruction[0].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), splittedInstruction[1].toOperand())
            } else if (splittedInstruction[1].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(splittedInstruction[0].toLabel(), splittedInstruction[1].toOpcode(), null)
            }
            //如果是切出三段字串 有可能是(Label+指令+operand)
            3 -> if (splittedInstruction[1].isRealOpcodeOrPseudoOpcode())
                return InstructionLine(
                    splittedInstruction[0].toLabel(),
                    splittedInstruction[1].toOpcode(),
                    splittedInstruction[2].toOperand()
                )

        }
        //如果是其他狀況就代表一定有問題
        println("Your assembly code has problem.")
        return null
    }

    //把字串List轉成指令物件List
    fun List<String>.toInstructions(): List<InstructionLine> {
        val instructionList: MutableList<InstructionLine> = mutableListOf()
        //一行一行的轉換
        for (instructionString in this) {
            //如果toInstruction()回傳的不是null才代表這行是個合法指令
            instructionString.toInstruction()?.let {
                instructionList.add(it)   //把回傳的合法指令存起來
            }
        }
        return instructionList
    }

    //生成出完整的一行T record
    //需要傳入這個T record的起始位址(10進位) , T record的長度(10進位), T record內的指令(已轉換成16進為表示的)
    fun getFullTRecord(startAddress: Int, length: Int, tRecordLine: String): String {
        var resultString = "T" //T record的開頭
        resultString += Integer.toHexString(startAddress).padStart(6, '0').uppercase() //T record的起始位址
        resultString += Integer.toHexString(length).padStart(2, '0').uppercase()  //T record的長度
        resultString += tRecordLine  //T record內的指令
        return resultString
    }

    //組譯
    fun assemble(): List<String> {
        var resultStringList: MutableList<String> = mutableListOf<String>() //最後要回傳的obj結果
        var locationCounter = 0  //location counter的位址(10進位)
        var startAddress = 0   //程式的起始位址(10進位)
        val instructionList = inputLines.toInstructions()  //把輸入的指令字串轉成指令物件List
        val symbolTable: MutableMap<String, Int> = mutableMapOf()  //符號(Label)表


        //pass 1
        for (currentLine in instructionList) {
            //如果有start代表有設定程式起始位置
            if (currentLine.opCode == OPCode.START) {
                startAddress = currentLine.operand?.value?.toInt(16)!!
                locationCounter = startAddress
            }

            //如果讀到的這行指令有label 就要把label記錄起來
            if (currentLine.label != null) {
                //重複Label是有問題的
                if (symbolTable.get(currentLine.label.name) != null) {
                    println("duplicate label")
                    exitProcess(-1)
                } else {
                    symbolTable.put(currentLine.label.name, locationCounter)
                }
            }

            if (currentLine.isRealOpcode()) {
                //如果讀到的這行指令是個真指令 就紀錄這行指令的長度 (加到location counter中)
                locationCounter += currentLine.opCode?.instructionLength!!
            } else if (currentLine.opCode == OPCode.WORD) {
                //如果讀到的這行指令是WORD 就加一個WORD的長度到location counter中
                locationCounter += WORD_SIZE
            } else if (currentLine.opCode == OPCode.RESW) {
                //如果讀到的這行指令是RESW 就加operand的數字*WORD_SIZE的長度到location counter中
                locationCounter += WORD_SIZE * currentLine.operand?.value?.toInt()!!
            } else if (currentLine.opCode == OPCode.RESB) {
                //如果讀到的這行指令是RESB 代表需要operand個byte的空間 (加到location counter中)
                locationCounter += currentLine.operand?.value?.toInt()!!
            } else if (currentLine.opCode == OPCode.BYTE) {
                //如果讀到的這行指令是BYTE 然後operand又是C開頭 代表要存的是字元(char) ，有幾個字元就需要幾個byte的空間
                //如果operand又是X開頭 代表要存的是數字 ，兩個(16進位)數字需要一個byte的空間
                //都要減3是因為要扣掉 C'' 或 X'' 所佔的三個字元
                locationCounter += currentLine.byteLength()!!
            }
        }
        val programLength = locationCounter - startAddress  //計算程式有多長

        //pass 2
        locationCounter = startAddress  //location counter重新計算
        //先處理 H record (Header)
        if (instructionList[0].opCode == OPCode.START) {
            var headerString = ""
            headerString += "H"
            if (instructionList[0].label != null) {
                headerString += instructionList[0].label?.name?.padEnd(6, ' ')   //如果START有LABEL 就要記下來
            } else {
                headerString += "".padEnd(6, ' ')  //如果START沒有LABEL 就留空
            }
            headerString += Integer.toHexString(startAddress).uppercase().padStart(6, '0')  //紀錄程式起始位址
            headerString += Integer.toHexString(programLength).uppercase().padStart(6, '0')  //紀錄程式長度
            resultStringList.add(headerString)
        }

        //紀錄目前這行T record的長度 (不含前置資訊長度超過60個字元(或是30byte)就樣要換行)
        //SIC中一個指令3個byte 要6個16進位數字來表示 代表一行T record能放10個SIC指令
        var currentTRecordLength = 0
        var currentTRecordString = ""
        var currentTRecordStartAddress = locationCounter
        var hasToChangeLine = false  //如果遇到RESW或RESB的區塊，下一次就要換行

        for (currentLine in instructionList) {

            if (currentLine.opCode == OPCode.START) {
                //START已經處理過了 就跳過
                hasToChangeLine = false
                continue
            } else if (currentLine.opCode == OPCode.END) {
                //END代表要結束了
                //縣看還有沒有剩下的T record需要紀錄的
                if (currentTRecordString != "" || hasToChangeLine) {
                    resultStringList.add(
                        getFullTRecord(
                            currentTRecordStartAddress,
                            currentTRecordLength,
                            currentTRecordString
                        )
                    )

                }
                hasToChangeLine = false
                //紀錄E record
                resultStringList.add("E" + Integer.toHexString(startAddress).uppercase().padStart(6, '0'))

            } else if (currentLine.isRealOpcode()) {
                //如果這行指令是個真指令
                var currentLineString = currentLine.opCode?.toHexString()?.uppercase() ?: ""  //先取出opcode的16進位表示
                //看看這行指令有沒有用isIndexedAddressing
                //如果有 就把X_bit設為1
                var xAndAddressBinaryString = if (currentLine.isIndexedAddressing()) "1" else "0"   //先以2進位紀錄_bit和address欄位

                if (currentLine.operand == null) {
                    xAndAddressBinaryString += "000000000000000" //如果這行指令沒有operand，address就全部填0
                } else {
                    //如果這行指令有operand，就去symbol table中找這個label所對應的address
                    val addressBinary =
                        Integer.toBinaryString(symbolTable.get(currentLine.getIndexForSymbolTable()) ?: 0)
                    xAndAddressBinaryString += addressBinary.padStart(15, '0')  //沒有滿要補0
                }

                //把以進位紀錄_bit和address欄位轉成16進位表示
                currentLineString += Integer.toHexString(xAndAddressBinaryString.toInt(2)).padStart(4, '0').uppercase()

                //如果目前這行的T record已經滿了(最多30byte) 就要換行
                if (locationCounter + currentLine.opCode?.instructionLength!! - currentTRecordStartAddress > 30 || hasToChangeLine) {
                    //把邵一段T record寫到結果中
                    resultStringList.add(
                        getFullTRecord(
                            currentTRecordStartAddress,
                            currentTRecordLength,
                            currentTRecordString
                        )
                    )
                    //重新計算T record的起始位址，長度，並清空T record的字串
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter

                }
                hasToChangeLine = false
                //記錄到T record
                locationCounter += currentLine.opCode.instructionLength
                currentTRecordString += currentLineString
                currentTRecordLength += currentLine.opCode.instructionLength
            } else if (currentLine.opCode == OPCode.WORD) {
                //如果這行指令是WORD
                //先把operand以16進位記下來
                val currentLineString =
                    Integer.toHexString(currentLine.operand?.value?.toInt()!!).uppercase().padStart(6, '0')
                if (locationCounter + WORD_SIZE - currentTRecordStartAddress > 30 || hasToChangeLine) {
                    resultStringList.add(
                        getFullTRecord(
                            currentTRecordStartAddress,
                            currentTRecordLength,
                            currentTRecordString
                        )
                    )
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter
                }
                hasToChangeLine = false
                locationCounter += WORD_SIZE
                currentTRecordString += currentLineString
                currentTRecordLength += WORD_SIZE

            } else if (currentLine.opCode == OPCode.BYTE) {
                //如果這行指令是BYTE
                //就要判斷用戶要存Hex還是Char(String)
                //如果是Hex就直接存下來
                //如果是Char(String)就要先把operand轉成16進位表示再存下來
                val currentLineString =
                    if (currentLine.operand?.startWithX()!!) currentLine.operand.value.substring(
                        2,
                        currentLine.operand.value.length - 1
                    ).uppercase() else currentLine.operand.cToAscii()
                if (locationCounter + currentLine.byteLength()!! - currentTRecordStartAddress > 30 || hasToChangeLine) {
                    resultStringList.add(
                        getFullTRecord(
                            currentTRecordStartAddress,
                            currentTRecordLength,
                            currentTRecordString
                        )
                    )
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter
                }
                hasToChangeLine = false
                locationCounter += currentLine.byteLength()!!
                currentTRecordString += currentLineString
                currentTRecordLength += currentLine.byteLength()!!
            } else if (currentLine.opCode == OPCode.RESW) {
                //如果讀到的這行指令是RESW 就加operand的數字*WORD_SIZE的長度到location counter中
                locationCounter += WORD_SIZE * currentLine.operand?.value?.toInt()!!
                hasToChangeLine = true
            } else if (currentLine.opCode == OPCode.RESB) {
                //如果讀到的這行指令是RESB 代表需要operand個byte的空間 (加到location counter中)
                locationCounter += currentLine.operand?.value?.toInt()!!
                hasToChangeLine = true
            }
        }
        return resultStringList
    }

}

fun String.isRealOpcode() = enumHasString<OPCode>(this) && !OPCode.valueOf(this).isPseudo  //如果字串是真的指令就回傳true
fun String.toOpcode(): OPCode = OPCode.valueOf(this)  //字串轉換成OPCode ENUM 型態
fun String.toOperand(): Operand = Operand(this)  //字串轉換成Operand型態
fun String.toLabel(): Label = Label(this)  //字串轉換成Label型態
fun String.isPseudoOpcode() = enumHasString<OPCode>(this) && OPCode.valueOf(this).isPseudo //如果字串是pseudo指令就回傳true
fun String.isRealOpcodeOrPseudoOpcode() = enumHasString<OPCode>(this) //如果字串是真的指令或是pseudo指令就回傳true


//這個enum中有沒有這個名字的值存在
inline fun <reified T : Enum<T>> enumHasString(name: String): Boolean {
    return enumValues<T>().any { it.name == name }
}

data class Operand(val value: String) {
    fun startWithX() = this.value[0] == 'X'  //如果operand的第一個字是X(代表存的是Hex)就回傳true
    fun startWithC() = this.value[0] == 'C'  //如果operand的第一個字是C(代表存的是Char)就回傳true

    //將operand中的多個Char轉成已16進位表示的Ascii code字串
    //兩個16進位數字能表示一個字元
    fun cToAscii(): String? {
        if (!startWithC()) return null
        val convertLine = value.substring(2, value.length - 1).uppercase()
        var resultString = ""
        for (c in convertLine) {
            resultString += Integer.toHexString(c.code).uppercase()
        }
        return resultString
    }
}

data class Label(val name: String)  //標籤

data class InstructionLine(val label: Label?, val opCode: OPCode?, val operand: Operand?) {
    fun isRealOpcode() = this.opCode != null && !(this.opCode.isPseudo)
    fun isPseudoOpcode() = this.opCode != null && this.opCode.isPseudo
    fun isIndexedAddressing(): Boolean {
        if (operand == null) return false
        return this.operand.value.uppercase().endsWith(",X") //如果operand的最後一個字是,X就代表是Indexed Addressing
    }

    //計算這行指令的byte長度
    fun byteLength(): Int? {
        if (opCode != OPCode.BYTE || operand == null) {
            return null
        }
        //如果讀到的這行指令是BYTE 然後operand又是C開頭 代表要存的是字元(char) ，有幾個字元就需要幾個byte的空間
        //如果operand又是X開頭 代表要存的是數字 ，兩個(16進位)數字需要一個byte的空間
        //都要減3是因為要扣掉 C'' 或 X'' 所佔的三個字元
        return if (operand.startWithC()) operand.value.length - 3 else (operand.value.length - 3) / 2
    }
    //取去SymbolTable中搜尋的 key, 因為有可能operand中帶有,X
    fun getIndexForSymbolTable(): String {
        if (isIndexedAddressing()) {
            return this.operand?.value?.substring(0, this.operand.value.length - 2)?.trim()!!
        }
        return this.operand?.value!!
    }
}


//opcode
enum class OPCode(val hex: Int, val isPseudo: Boolean = false, val instructionLength: Int = WORD_SIZE) {
    ADD(0x18),
    AND(0x40),
    COMP(0x28),
    DIV(0x24),
    J(0x3C),
    JEQ(0x30),
    JGT(0x34),
    JLT(0x38),
    JSUB(0x48),
    LDA(0x00),
    LDCH(0x50),
    LDL(0x08),
    LDX(0x04),
    MUL(0x20),
    OR(0x44),
    RD(0xD8),
    RSUB(0x4C),
    STA(0x0C),
    STCH(0x54),
    STL(0x14),
    STSW(0xE8),
    STX(0x10),
    SUB(0x1C),
    TD(0xE0),
    TIX(0x2C),
    WD(0xDC),
    START(PSEUDO, true, 0),
    WORD(PSEUDO, true, 0),
    BYTE(PSEUDO, true, 0),
    RESW(PSEUDO, true, 0),
    RESB(PSEUDO, true, 0),
    END(PSEUDO, true, 0);

    //將opcode轉乘16進位表示的字串
    fun toHexString(): String {
        return Integer.toHexString(hex).uppercase().padStart(2, '0')
    }
}

