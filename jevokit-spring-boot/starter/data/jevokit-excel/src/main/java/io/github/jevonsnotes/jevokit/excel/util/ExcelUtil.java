package io.github.jevonsnotes.jevokit.excel.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Filter;
import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.github.jevonsnotes.jevokit.core.holder.IntHolder;
import io.github.jevonsnotes.jevokit.core.lang.CallbackR;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * excel操作工具
 */
@Slf4j
public class ExcelUtil {


    /**
     * 大文件写出
     *
     * @param in         输入流
     * @param out        输出流
     * @param sheetIndex 操作sheet
     * @param startRow   开始行数
     * @param rows       行数据 （一次性写出）
     * @throws IOException
     */
    public static <T> void bigWrite(InputStream in, OutputStream out, int sheetIndex, int startRow, List<T> rows) throws IOException {
        if(ExcelFileUtil.isXlsx(in)){
            bigWrite(new XSSFWorkbook(in),out,sheetIndex, startRow, rows);
        } else {
            throw new RuntimeException("bigWrite method only support format of xlsx");
        }
    }
    /**
     * 大文件写出
     *
     * @param sheets      sheets
     * @param out        输出流
     * @param sheetIndex 操作sheet
     * @param startRow   开始行数
     * @param rows       行数据 （一次性写出）
     * @throws IOException
     */
    public static <T> void bigWrite(XSSFWorkbook sheets, OutputStream out, int sheetIndex, int startRow, List<T> rows) throws IOException {
        IntHolder flag = new IntHolder(0);
        bigWrite(sheets, out, sheetIndex, startRow, () -> flag.value++ == 0 ? rows : null,true);
    }
    /**
     * 大文件写出
     *
     * @param in         输入流
     * @param out        输出流
     * @param sheetIndex 操作sheet
     * @param startRow   开始行数
     * @param loopBack   循环写出，返回list == null 或 list.size()==0 则停止写出
     * @throws IOException
     */
    public static void bigWrite(InputStream in, OutputStream out, int sheetIndex, int startRow, CallbackR<? extends List> loopBack,boolean closeOut) throws IOException {
        if(ExcelFileUtil.isXlsx(in)){
            bigWrite(new XSSFWorkbook(in),out,sheetIndex,startRow,loopBack,closeOut);
        } else {
            throw new RuntimeException("bigWrite method only support format of xlsx");
        }
    }

    /**
     * 大文件写出
     *
     * @param sheets         输入流
     * @param out        输出流
     * @param sheetIndex 操作sheet
     * @param startRow   开始行数
     * @param loopBack   循环写出，返回list == null 或 list.size()==0 则停止写出
     * @throws IOException
     */
    public static void bigWrite(XSSFWorkbook sheets, OutputStream out, int sheetIndex, int startRow, CallbackR<? extends List> loopBack, boolean closeOut) throws IOException {

        XSSFSheet sheetAt = sheets.getSheetAt(sheetIndex);
        //删除无效行，避免报错
        removeRows(sheetAt,startRow);

        SXSSFWorkbook sxssfWorkbook = new SXSSFWorkbook(sheets);
        SXSSFSheet sheet = sxssfWorkbook.getSheetAt(sheetIndex);
        BigExcelWriter writer = new BigExcelWriter(sheet);

        writer.setSheet(sheetIndex);//写入指定sheet
        writer.setCurrentRow(startRow);//从指定行开始写入开始

        try {
            List rows;
            while ((rows = loopBack.call()) != null && rows.size() > 0) {
                CollectionUtil.filter(rows, (Filter) ele -> ele != null);
                writer.write(rows, false);
            }
            writer.flush(out,closeOut);
        } finally {
            sheets.close();
            writer.close();
        }

    }

    /**
     * 删除sheet行
     * @param sheet
     * @param startRow 从startRow开始删
     * @throws IOException
     */
    public static void removeRows(Sheet sheet, int startRow) throws IOException {
        //删除无效行，避免报错
        for (int i = sheet.getLastRowNum(); i > startRow; i--) {
            Row row = sheet.getRow(i);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }
}
