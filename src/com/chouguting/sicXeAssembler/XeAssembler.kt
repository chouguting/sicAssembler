package com.chouguting.sicXeAssembler

import kotlin.system.exitProcess

const val PSEUDO = -1    // Pseudo opcode
const val WORD_SIZE = 3  //SIC中一個word的長度是3個byte

class XeAssembler(var inputLines: List<String> = listOf()) {
    //把單行字串轉成指令
    private fun String.toInstruction(): InstructionLine? {
        if (this.isEmpty()) return null  //如果是空字串 就不處理

        if (this.trim()[0] == '.' || this.trim()[0] == '\n') return null //如果是註解或換行 就不處理
        val splittedInstruction =
            this.uppercase().trim().split(" ", "\t").toMutableList() //將字串切割成不同部分(LABEL,OPCODE,OPERAND，雖然不一定都有)

        var isFormat4 = false //是否是format4的指令
        //如果有加號代表是Format 4的指令
        if(splittedInstruction[0].startsWith("+")){
            isFormat4 = true
            splittedInstruction[0] = splittedInstruction[0].substring(1)
        }
        if(splittedInstruction.size >=2 && splittedInstruction[1].startsWith("+")){
            isFormat4 = true
            splittedInstruction[1] = splittedInstruction[1].substring(1)
        }

        when (splittedInstruction.size) {
            //如果只有切出一段字串 代表一定是只有一個指令(Format 1)
            1 -> if (splittedInstruction[0].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), null, isFormat4)
            }
            //如果是切出兩段字串 有可能是(Label+指令) 或是 (指令+operand)
            2 -> if (splittedInstruction[0].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(null, splittedInstruction[0].toOpcode(), splittedInstruction[1].toOperand(), isFormat4)
            } else if (splittedInstruction[1].isRealOpcodeOrPseudoOpcode()) {
                return InstructionLine(splittedInstruction[0].toLabel(), splittedInstruction[1].toOpcode(), null, isFormat4)
            }
            //如果是切出三段字串 有可能是(Label+指令+operand)
            3 -> if (splittedInstruction[1].isRealOpcodeOrPseudoOpcode())
                return InstructionLine(
                    splittedInstruction[0].toLabel(),
                    splittedInstruction[1].toOpcode(),
                    splittedInstruction[2].toOperand(),
                    isFormat4
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
        var mRecordList: MutableList<String> = mutableListOf<String>() //最後要回傳的m record
        var locationCounter = 0  //location counter的位址(10進位)
        var startAddress = 0   //程式的起始位址(10進位)
        var baseAddress = 0    //base address的位址(10進位)
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
                locationCounter += currentLine.instructionLength
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
                //先看還有沒有剩下的T record需要紀錄的
                if (currentTRecordString != "" || hasToChangeLine) {
                    resultStringList.add(
                        getFullTRecord(
                            currentTRecordStartAddress,
                            currentTRecordLength,
                            currentTRecordString
                        )
                    )

                }
                //把M record記錄下來
                for(mRecord in mRecordList){
                    resultStringList.add(mRecord)
                }
                hasToChangeLine = false
                //紀錄E record
                resultStringList.add("E" + Integer.toHexString(startAddress).uppercase().padStart(6, '0'))

            }else if(currentLine.opCode == OPCode.BASE){
                //BASE是一個設定 base address的虛假指令
                baseAddress = symbolTable.get(currentLine.getIndexForSymbolTable()) ?: 0
            } else if (currentLine.isRealOpcode()) {



                //如果這行指令是個真指令
                var currentLineString = currentLine.opCode?.toHexString()?.uppercase() ?: ""  //先取出opcode的16進位表示
                if(currentLine.opCode?.format == 3){
                    //nixbpe中的ni
                    if(currentLine.isImmediateAddressing()){
                        currentLineString = currentLine.opCode?.toImmediateHexString()?.uppercase() ?: ""
                    }else if(currentLine.isIndirectAddressing()){
                        currentLineString = currentLine.opCode?.toIndirectHexString()?.uppercase() ?: ""
                    }else{
                        currentLineString = currentLine.opCode?.toXeHexString()?.uppercase() ?: ""
                    }


                    //nixbpe中的bpe
                    //看看這行指令有沒有用isIndexedAddressing
                    //如果有 就把X_bit設為1
                    var xbpe_String = if (currentLine.isIndexedAddressing()) "1" else "0"   //先以2進位紀錄_bit和address欄位

                    //如果找不到LABEL 又不是立即定址 代表有問題
                    if(currentLine.operand!= null && symbolTable.get(currentLine.getIndexForSymbolTable()) == null && !currentLine.isImmediateAddressing()){
                        println("($locationCounter) label not found")
                        exitProcess(-1)
                    }

                    if(currentLine.operand==null){
                        //如果沒有operand 代表後面都要填零
                        if(currentLine.isFormat4()){
                            currentLineString += "".padStart(6, '0')
                        }else{
                            currentLineString += "".padStart(4, '0')
                        }
                    }else if(currentLine.isFormat4()){
                        //format 4 的指令 bpe是001
                        xbpe_String += "001"
                        currentLineString += Integer.toHexString(xbpe_String.toInt(2)).uppercase()  //再把xbpe轉成16進位(4個bit變1個Hex字元)

                        //如果這行指令是format 4，就去symbol table中找這個label所對應的絕對address
                        //如果找不到這個label ,就先假設他是立即值
                        var addressBinary =
                            Integer.toBinaryString(symbolTable.get(currentLine.getIndexForSymbolTable()) ?: currentLine.getIndexForSymbolTable().toInt())

                        addressBinary = addressBinary.padStart(20, '0')  //沒有滿要補0
                        //把address欄位轉成16進位表示 (20個bit變5個Hex字元)
                        currentLineString += Integer.toHexString(addressBinary.toInt(2)).padStart(5, '0').uppercase()

                        //如果是format4，又沒有用直接定址(代表需要reference到其他地方)，就要記錄到m record中
                        if(!currentLine.isImmediateAddressing()){
                            var mRecord = "M"
                            mRecord += Integer.toHexString(locationCounter+1).padStart(6, '0').uppercase()
                            mRecord += "05"
                            mRecordList.add(mRecord)
                        }
                    }else{
                        //format 3
                        //如果找不到這個label ,就假設他是立即值
                        var targetAddress = symbolTable.get(currentLine.getIndexForSymbolTable()) ?: currentLine.getIndexForSymbolTable().toInt() //目標位址

                        //先試著用PC relative addressing
                        val pc_address = locationCounter + currentLine.instructionLength
                        val pcOffset = targetAddress - pc_address
                        val baseOffset = targetAddress - baseAddress
                        var displacement = ""

                        if(symbolTable.get(currentLine.getIndexForSymbolTable()) == null && currentLine.isImmediateAddressing()){
                            //如果找不到這個label 又是立即定址 代表是個單純的立即值
                            displacement = "%04x".format(targetAddress).takeLast(4).uppercase()
                        }else if(pcOffset >= -2048 && pcOffset <= 2047){
                            //如果PC relative addressing可以用
                            //bpe是010
                            xbpe_String += "010"
                            currentLineString += Integer.toHexString(xbpe_String.toInt(2)).uppercase()  //再把xbpe轉成16進位(4個bit變1個Hex字元)
                            //把PC relative addressing的offset轉成16進位表示 (12個bit變3個Hex字元)
                            displacement = "%03x".format(pcOffset).takeLast(3).uppercase()
                        }else if(baseAddress >= 0 && baseOffset <= 4095){
                            //PC relative addressing不行 再用base relative addressing
                            //如果base relative addressing可以用
                            //bpe是011
                            xbpe_String += "100"
                            currentLineString += Integer.toHexString(xbpe_String.toInt(2)).uppercase()  //再把xbpe轉成16進位(4個bit變1個Hex字元)
                            //把base relative addressing的offset轉成16進位表示 (12個bit變3個Hex字元)
                            displacement = "%03x".format(baseOffset).takeLast(3).uppercase()
                        }else{
                            //PC BASE都不行就提醒用戶要改用format 4
                            println("($locationCounter)you need to use format 4")
                            exitProcess(-1)
                        }
                        currentLineString += displacement

                    }
                }else if(currentLine.opCode?.format == 2){
                    //拆成兩個Register代號
                    currentLineString+=currentLine.operand?.toFormat2RegisterField()
                }else if(currentLine.opCode?.format == 1){
                    //不用做事
                }

                //如果目前這行的T record已經滿了(最多30byte) 就要換行
                if (locationCounter + currentLine.instructionLength - currentTRecordStartAddress > 30 || hasToChangeLine) {
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
                locationCounter += currentLine.instructionLength
                currentTRecordString += currentLineString
                currentTRecordLength += currentLine.instructionLength
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
fun String.toRegister(): Register = Register.valueOf(this.uppercase())  //字串轉換成Register型態
fun String.isPseudoOpcode() = enumHasString<OPCode>(this) && OPCode.valueOf(this).isPseudo //如果字串是pseudo指令就回傳true
fun String.isRealOpcodeOrPseudoOpcode() = enumHasString<OPCode>(this) //如果字串是真的指令或是pseudo指令就回傳true


//這個enum中有沒有這個名字的值存在
inline fun <reified T : Enum<T>> enumHasString(name: String): Boolean {
    return enumValues<T>().any { it.name == name }
}

data class Operand(val value: String) {
    fun startWithX() = this.value[0] == 'X'  //如果operand的第一個字是X(代表存的是Hex)就回傳true
    fun startWithC() = this.value[0] == 'C'  //如果operand的第一個字是C(代表存的是Char)就回傳true

    fun startWithHash() = this.value[0] == '#'  //如果operand的第一個字是#(代表是立即定址)就回傳true
    fun startWithAt() = this.value[0] == '@'  //如果operand的第一個字是@(代表是間接對定址)就回傳true

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

    fun toFormat2RegisterField(): String {
        val splitOperand = value.split(",")
        if(splitOperand.size == 2) {
            return splitOperand[0].toRegister().toHexString() + splitOperand[1].toRegister().toHexString()
        }
        return  splitOperand[0].toRegister().toHexString() + "0"
    }
}

enum class Register(val hexCode:Int){
    A(0x0),
    X(0x1),
    L(0x2),
    PC(0x8),
    SW(0x9),
    B(0x3),
    S(0x4),
    T(0x5),
    F(0x6);
    //將opcode轉乘16進位表示的字串
    fun toHexString(): String {
        return Integer.toHexString(hexCode).uppercase().padStart(1, '0')
    }
}

data class Label(val name: String)  //標籤

data class InstructionLine(val label: Label?, val opCode: OPCode?, val operand: Operand?,val format4:Boolean = false) {

    val instructionLength: Int
        get() {
            if(format4) return 4
            return opCode?.format ?: 3
        }

    fun isRealOpcode() = this.opCode != null && !(this.opCode.isPseudo)
    fun isPseudoOpcode() = this.opCode != null && this.opCode.isPseudo
    fun isIndexedAddressing(): Boolean {
        if (operand == null) return false
        return this.operand.value.uppercase().endsWith(",X") //如果operand的最後一個字是,X就代表是Indexed Addressing
    }

    fun isFormat4():Boolean{
        return this.format4
    }

    fun isImmediateAddressing(): Boolean {
        if (operand == null) return false
        return this.operand.startWithHash() //如果operand的第一個字是#就代表是立即定址
    }

    fun isIndirectAddressing(): Boolean {
        if (operand == null) return false
        return this.operand.startWithAt() //如果operand的第一個字是@就代表是間接對定址
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

    //取去SymbolTable中搜尋的 key, 因為有可能operand中帶有,X  # 或 @
    fun getIndexForSymbolTable(): String {
        if (isIndexedAddressing()) {
            //去掉operand中的 ,X
            return this.operand?.value?.substring(0, this.operand.value.length - 2)?.trim()!!
        }

        if(isImmediateAddressing() || isIndirectAddressing()){
            //去掉operand中的 # 或是 @
            return this.operand?.value?.substring(1,this.operand.value.length)?.trim()!!
        }
        return this.operand?.value!!
    }
}


//opcode
//預設是format 3
enum class OPCode(val hex: Int, val isPseudo: Boolean = false,val format:Int = 3) {
    ADD(0x18),
    ADDF(0X58),
    ADDR(0x90, format = 2),
    AND(0x40),
    CLEAR(0xB4, format = 2),
    COMP(0x28),
    COMPF(0x88),
    COMPR(0xA0, format = 2),
    DIV(0x24),
    DIVF(0x64),
    DIVR(0x9C, format = 2),
    FIX(0xC4, format = 1),
    FLOAT(0xC0, format = 1),
    HIO(0xF4, format = 1),
    J(0x3C),
    JEQ(0x30),
    JGT(0x34),
    JLT(0x38),
    JSUB(0x48),
    LDA(0x00),
    LDB(0x68),
    LDCH(0x50),
    LDF(0x70),
    LDL(0x08),
    LDS(0x6C),
    LDT(0x74),
    LDX(0x04),
    LPS(0xD0),
    MUL(0x20),
    MULF(0x60),
    MULR(0x98, format = 2),
    NORM(0xC8, format = 1),
    OR(0x44),
    RD(0xD8),
    RMO(0xAC, format = 2),
    RSUB(0x4C),
    SHIFTL(0xA4, format = 2),
    SHIFTR(0xA8, format = 2),
    SIO(0xF0, format = 1),
    SSK(0xEC),
    STA(0x0C),
    STB(0x78),
    STCH(0x54),
    STF(0x80),
    STI(0xD4),
    STL(0x14),
    STS(0x7C),
    STSW(0xE8),
    STT(0x84),
    STX(0x10),
    SUB(0x1C),
    SUBF(0x5C),
    SUBR(0x94, format = 2),
    SVC(0xB0, format = 2),
    TD(0xE0),
    TIO(0xF8, format = 1),
    TIX(0x2C),
    TIXR(0xB8, format = 2),
    WD(0xDC),
    START(PSEUDO, true, 0),
    WORD(PSEUDO, true, 0),
    BYTE(PSEUDO, true, 0),
    RESW(PSEUDO, true, 0),
    RESB(PSEUDO, true, 0),
    BASE(PSEUDO,true,0),
    END(PSEUDO, true, 0);


    //將opcode轉乘16進位表示的字串
    fun toHexString(): String {
        return Integer.toHexString(hex).uppercase().padStart(2, '0')
    }

    fun toIndirectHexString(): String{
        //n_bit 設為1
        return Integer.toHexString(hex+0x2).uppercase().padStart(2, '0')
    }

    fun toImmediateHexString(): String{
        //i_bit 設為1
        return Integer.toHexString(hex+0x1).uppercase().padStart(2, '0')
    }

    fun toXeHexString(): String{
        //n_bit和i_bit 都設為1
        return Integer.toHexString(hex+0x3).uppercase().padStart(2, '0')
    }

}

