package com.chouguting.sicAssembler

import kotlin.system.exitProcess

const val PSEUDO = -1
const val WORD_SIZE = 3

class Assembler(var inputLines: List<String> = listOf()) {
    //把單行字串轉成指令
    private fun String.toInstruction(): InstructionLine? {
        if (this.isEmpty()) return null

        if (this.trim()[0] == '.' || this.trim()[0] == '\n') return null
        val splittedInstruction = this.uppercase().trim().split(" ", "\t")
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

    fun getFullTRecord(startAddress: Int, length: Int, tRecordLine: String): String {
        var resultString = "T"
        resultString += Integer.toHexString(startAddress).padStart(6, '0').uppercase()
        resultString += Integer.toHexString(length).padStart(2, '0').uppercase()
        resultString += tRecordLine
        return resultString
    }

    //組譯
    fun assemble(): String {
        var resultString = ""
        var locationCounter = 0
        var startAddress = 0
        val instructionList = inputLines.toInstructions()
        val symbolTable: MutableMap<String, Int> = mutableMapOf()


        //pass 1
        for (currentLine in instructionList) {
            //如果有start代表有設定程式起始位置
            if (currentLine.opCode == OPCode.START) {
                startAddress = currentLine.operand?.value?.toInt(16)!!
                locationCounter = startAddress
            }

            //如果讀到的這行指令有label 就要把label記錄起來
            if (currentLine.label != null) {
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
        locationCounter = startAddress
        if (instructionList[0].opCode == OPCode.START) {
            resultString += "H"
            if (instructionList[0].label?.name != null) {
                resultString += instructionList[0].label?.name?.padEnd(6, ' ')
            }
            resultString += Integer.toHexString(startAddress).uppercase().padStart(6, '0')
            resultString += Integer.toHexString(programLength).uppercase().padStart(6, '0')
            resultString += "\n"
        }

        //紀錄目前這行T record的長度 (不含前置資訊長度超過60個字元(或是30byte)就樣要換行)
        //SIC中一個指令3個byte 要6個16進位數字來表示 代表一行T record能放10個SIC指令
        var currentTRecordLength = 0
        var currentTRecordString = ""
        var currentTRecordStartAddress = locationCounter

        for (currentLine in instructionList) {
            var operandAddress = 0

            if (currentLine.opCode == OPCode.START) {
                continue
            } else if (currentLine.opCode == OPCode.END) {
                if (currentTRecordString != "") {
                    resultString += getFullTRecord(
                        currentTRecordStartAddress,
                        currentTRecordLength,
                        currentTRecordString
                    )
                    resultString += "\n"
                }
                resultString += "E" + Integer.toHexString(startAddress).uppercase().padStart(6, '0')

            } else if (currentLine.isRealOpcode()) {

                //如果這行指令是個真指令
                var currentLineString = currentLine.opCode?.toHexString()?.uppercase()?.padStart(2,'0') ?: ""
                var xAndAddressBinaryString = if (currentLine.isIndexedAddressing()) "1" else "0"
                if (currentLine.operand == null) {
                    xAndAddressBinaryString += "000000000000000"
                } else {
                    val addressBinary = Integer.toBinaryString(symbolTable.get(currentLine.getIndexForSymbolTable()) ?: 0)
                    xAndAddressBinaryString += addressBinary.padStart(15, '0')
                }
                currentLineString += Integer.toHexString(xAndAddressBinaryString.toInt(2)).padStart(4,'0').uppercase()

                if (locationCounter + currentLine.opCode?.instructionLength!! - currentTRecordStartAddress > 30) {
                    resultString += getFullTRecord(
                        currentTRecordStartAddress,
                        currentTRecordLength,
                        currentTRecordString
                    )
                    resultString += "\n"
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter
                }
                locationCounter += currentLine.opCode?.instructionLength!!
                currentTRecordString += currentLineString
                currentTRecordLength += currentLine.opCode.instructionLength
            } else if (currentLine.opCode == OPCode.WORD) {
                val currentLineString =
                    Integer.toHexString(currentLine.operand?.value?.toInt()!!).uppercase().padStart(6, '0')
                if (locationCounter + WORD_SIZE - currentTRecordStartAddress > 30) {
                    resultString += getFullTRecord(
                        currentTRecordStartAddress,
                        currentTRecordLength,
                        currentTRecordString
                    )
                    resultString += "\n"
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter
                }
                locationCounter += WORD_SIZE
                currentTRecordString += currentLineString
                currentTRecordLength += WORD_SIZE

            } else if (currentLine.opCode == OPCode.BYTE) {
                val currentLineString =
                    if (currentLine.operand?.startWithX()!!) currentLine.operand.value.substring(
                        2,
                        currentLine.operand.value.length - 1
                    ).uppercase() else currentLine.operand.cToAscii()
                if (locationCounter + currentLine.byteLength()!! - currentTRecordStartAddress > 30) {
                    resultString += getFullTRecord(
                        currentTRecordStartAddress,
                        currentTRecordLength,
                        currentTRecordString
                    )
                    resultString += "\n"
                    currentTRecordString = ""
                    currentTRecordLength = 0
                    currentTRecordStartAddress = locationCounter
                }
                locationCounter += currentLine.byteLength()!!
                currentTRecordString += currentLineString
                currentTRecordLength += currentLine.byteLength()!!
            } else if (currentLine.opCode == OPCode.RESW) {
                //如果讀到的這行指令是RESW 就加operand的數字*WORD_SIZE的長度到location counter中
                locationCounter += WORD_SIZE * currentLine.operand?.value?.toInt()!!
            } else if (currentLine.opCode == OPCode.RESB) {
                //如果讀到的這行指令是RESB 代表需要operand個byte的空間 (加到location counter中)
                locationCounter += currentLine.operand?.value?.toInt()!!
            }
        }
        return resultString
    }

}

fun String.isRealOpcode() = enumHasString<OPCode>(this) && !OPCode.valueOf(this).isPseudo
fun String.toOpcode(): OPCode = OPCode.valueOf(this)
fun String.toOperand(): Operand = Operand(this)
fun String.toLabel(): Label = Label(this)
fun String.isPseudoOpcode() = enumHasString<OPCode>(this) && OPCode.valueOf(this).isPseudo
fun String.isRealOpcodeOrPseudoOpcode() = enumHasString<OPCode>(this)


//這個enum中有沒有這個名字的值存在
inline fun <reified T : Enum<T>> enumHasString(name: String): Boolean {
    return enumValues<T>().any { it.name == name }
}

data class Operand(val value: String) {
    fun startWithX() = this.value[0] == 'X'
    fun startWithC() = this.value[0] == 'C'
    fun toSixBit() = this.value.format("%06d")

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

data class Label(val name: String)

data class InstructionLine(val label: Label?, val opCode: OPCode?, val operand: Operand?) {
    fun isRealOpcode() = this.opCode != null && !(this.opCode?.isPseudo!!)
    fun isPseudoOpcode() = this.opCode != null && this.opCode?.isPseudo
    fun isIndexedAddressing(): Boolean {
        if (operand == null) return false
        return this.operand?.value?.uppercase()?.endsWith(",X")!!
    }

    fun byteLength(): Int? {
        if (opCode != OPCode.BYTE || operand == null) {
            return null
        }
        return if (operand.startWithC()) operand.value.length - 3 else (operand.value.length - 3) / 2
    }

    fun getIndexForSymbolTable():String{
        if(isIndexedAddressing()){
            return this.operand?.value?.substring(0,this.operand.value.length-2)?.trim()!!
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

    fun toHexString(): String {
        return Integer.toHexString(hex).uppercase()
    }
}

