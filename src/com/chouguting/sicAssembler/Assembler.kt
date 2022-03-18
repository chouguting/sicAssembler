package com.chouguting.sicAssembler

import kotlin.system.exitProcess

const val PSEUDO = -1
const val WORD_SIZE = 3

class Assembler(var inputLines:List<String> = listOf()) {


    //把單行字串轉成指令
    private fun String.toInstruction():InstructionLine?{
        if(this.isEmpty()) return null

        if(this.trim()[0] == '.' || this.trim()[0] == '\n') return null
        val splittedInstruction = this.uppercase().trim().split(" ","\t")
        when (splittedInstruction.size) {
            1 -> if (splittedInstruction[0].isOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), null)
            }
            2 -> if (splittedInstruction[0].isOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), splittedInstruction[1].toOperand())
            } else if (splittedInstruction[1].isOpcodeOrPseudoOpcode()) {
                return InstructionLine(splittedInstruction[0].toLabel(), splittedInstruction[1].toOpcode(), null)
            }
            3 -> if (splittedInstruction[1].isOpcodeOrPseudoOpcode())
                return InstructionLine(
                    splittedInstruction[0].toLabel(),
                    splittedInstruction[1].toOpcode(),
                    splittedInstruction[2].toOperand()
                )

        }
        println("Your assembly code has problem.")
        return null
    }

    //把字串List轉成指令物件List
    fun List<String>.toInstructions():List<InstructionLine>{
        val instructionList:MutableList<InstructionLine> = mutableListOf()
        for(instructionString in this){
            instructionString.toInstruction()?.let {
                instructionList.add(it)
            }
        }
        return instructionList
    }

    fun assemble():String{
        var resultString = ""
        var locationCounter=0
        var startAddress = 0
        val instructionList = inputLines.toInstructions()
        val symbolTable:MutableMap<String,Int> = mutableMapOf()


        //pass 1
        for(currentLine in instructionList){
            if(currentLine.opCode == OPCode.START){
                startAddress = currentLine.operand?.value?.toInt(16)!!
                locationCounter = startAddress
            }
            if(currentLine.label != null){
                if(symbolTable.get(currentLine.label.name) != null){
                    println("duplicate label")
                    exitProcess(-1)
                }else{
                    symbolTable.put(currentLine.label.name , locationCounter)
                }
            }

            if(currentLine.opCode?.isPseudo!!){
                continue
            }else if(!currentLine.opCode.isPseudo){
                locationCounter += currentLine.opCode.instructionLength
            }else if(currentLine.opCode == OPCode.WORD){
                locationCounter += currentLine.opCode.instructionLength
            }else if(currentLine.opCode == OPCode.RESW){
                locationCounter += WORD_SIZE*currentLine.operand?.value?.toInt()!!
            }else if(currentLine.opCode == OPCode.RESB){
                locationCounter += if(currentLine.operand?.startWithC()!!) currentLine.operand.value.length-3 else (currentLine.operand.value.length-3)/2
            }else{
                println("invalid opcode")
                exitProcess(-1)
            }
        }
        val programLength = locationCounter - startAddress

        if(instructionList[0].opCode == OPCode.START){
            resultString += "H"
            if(instructionList[0].label?.name!=null) {
                resultString += instructionList[0].label?.name?.padStart(6, ' ')
            }
            resultString += Integer.toHexString(startAddress).padStart(6,'0')
            resultString += Integer.toHexString(programLength).padStart(6,'0')
        }

        //pass 2
        for(currentLine in instructionList){

        }
        return resultString
    }

}

fun String.isOpcode() = enumHasString<OPCode>(this) && !OPCode.valueOf(this).isPseudo
fun String.toOpcode():OPCode = OPCode.valueOf(this)
fun String.toOperand():Operand = Operand(this)
fun String.toLabel():Label = Label(this)
fun String.isPseudoOpcode() = enumHasString<OPCode>(this) && OPCode.valueOf(this).isPseudo
fun String.isOpcodeOrPseudoOpcode() =  enumHasString<OPCode>(this)
fun Operand.startWithC() = this.value[0]=='C'
fun Operand.startWithX() = this.value[0]=='X'
fun Operand.toSixBit() = this.value.format("%06d")


//這個enum中有沒有這個名字的值存在
inline fun <reified T : Enum<T>> enumHasString(name: String): Boolean {
    return enumValues<T>().any { it.name == name}
}

data class Operand(val value: String)

data class Label(val name: String)

data class InstructionLine(val label: Label?,val opCode: OPCode?,val operand: Operand?)


//opcode
enum class OPCode(val hex:Int,val isPseudo: Boolean = false,val instructionLength: Int = WORD_SIZE){
    ADD(0x18),
    AND (0x40),
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
    START(PSEUDO,true, 0),
    WORD(PSEUDO,true, 0),
    BYTE(PSEUDO,true,0),
    RESW(PSEUDO,true,0),
    RESB(PSEUDO,true,0),
    END(PSEUDO,true,0)
}

