# sicAssembler
使用Kotlin實作 SIC及 SIC XE的組譯器(Assembler)

使用方式：

1. 把 inputXe.asm(一定要叫這個名字) 和 sicXeAssembler.jar(在out/artifacts/sicXeAssembler_jar/...之中) 放到同個資料夾下，再點左鍵點兩下jar檔，他就會輸出一個 resultXe.obj檔案
2. 用command line(terminal) 跑出這個sicXeAssembler.jar (java -jar sicXeAssembler.jar) 後面可以給兩個參數，第一個是要被組譯的asm檔的完整檔名(包含副檔名) 第二個是要輸出的obj檔的完整檔名(包含副檔名)
