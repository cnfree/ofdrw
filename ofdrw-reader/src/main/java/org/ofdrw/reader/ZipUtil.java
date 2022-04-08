package org.ofdrw.reader;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtil {
    /**
     * 解压许可最大字节数，为了防止 ZIP炸弹攻击
     * <p>
     * 默认值： 100M
     */
    private static long MaxSize = 100 * 1024 * 1024;


    /**
     * 设置 解压许可最大字节数
     *
     * @param size 压缩文件解压最大大小,默认值： 100M
     */
    public static void setMaxSize(long size) {
        if (size <= 0) {
            size = 100 * 1024 * 1024;
        }
        MaxSize = size;
    }

    /**
     * 解压到指定目录
     *
     * @param zipPath 需要解压的文件路径
     * @param descDir 解压到目录
     * @throws IOException 文件操作IO异常
     */
    public static void unZipFiles(String zipPath, String descDir) throws IOException {
        unZipFiles(new File(zipPath), descDir);
    }

    /**
     * 解压文件到指定目录
     *
     * @param src     压缩文件流
     * @param descDir 解压到目录
     * @throws IOException 文件操作IO异常
     */
    public static void unZipFiles(InputStream src, String descDir) throws IOException {
        File pathFile = new File(descDir).getCanonicalFile();
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        int countByteNumber = 0;

        // 解决zip文件中有中文目录或者中文文件
        ZipInputStream zip = new ZipInputStream(src, Charset.forName("GBK"));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();

            File file = new File(pathFile, name).getCanonicalFile();

            //校验路径合法性
            pathValid(pathFile.getAbsolutePath(), file.getAbsolutePath());

            if (entry.isDirectory()) {
                file.mkdirs();
            } else {
                File dir = file.getParentFile();
                if (!dir.exists()) {
                    dir.mkdirs();
                }


                byte[] buf = new byte[1024];
                int num;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                while ((num = zip.read(buf, 0, buf.length)) != -1) {

                    //写入字节数超出限制则抛出异常
                    if (countByteNumber + num > MaxSize) {
                        throw new IOException(String.format("写入数据超出ZIP解压最大字节数(%s)限制！", MaxSize));
                    }

                    bos.write(buf, 0, num);

                    countByteNumber += num;
                }
                Files.write(Paths.get(file.getAbsolutePath()), bos.toByteArray());
            }
        }
    }

    /**
     * 解压文件到指定目录
     *
     * @param zipFile 需要解压的文件
     * @param descDir 解压到目录
     * @throws IOException 文件操作IO异常
     */
    public static void unZipFiles(File zipFile, String descDir) throws IOException {
        unZipFileByApacheCommonCompress(zipFile, descDir);
    }

    /**
     * 校验文件路径是否在期望的文件目录下
     *
     * @param targetDir 期望解压目录
     * @param filePath  文件路径
     * @throws IOException 文件操作IO异常
     */
    private static void pathValid(String targetDir, String filePath) throws IOException {
        if (!filePath.startsWith(targetDir))
            throw new IOException(String.format("不合法的路径：%s", filePath));
    }

    /**
     * 使用apache common compress库 解压zipFile，能支持更多zip包解压的特性
     *
     * @param srcFile 带解压的源文件
     * @param descDir 解压到目录
     * @throws IOException
     */

    public static void unZipFileByApacheCommonCompress(File srcFile, String descDir) throws IOException {
        try {
            unZipFileByApacheCommonCompress(srcFile, descDir, "UTF-8");
        } catch (Exception e) {
            unZipFileByApacheCommonCompress(srcFile, descDir, "GBK");
        }
    }

    public static void unZipFileByApacheCommonCompress(File srcFile, String descDir, String encoding) throws IOException {
        InputStream inputStream = null;//源文件输入流，用于构建 ZipArchiveInputStream
        OutputStream outputStream = null;//解压缩的文件输出流
        ZipArchiveInputStream zipArchiveInputStream = null;//zip 文件输入流
        ArchiveEntry archiveEntry = null;//压缩文件实体.
        try {
            inputStream = new FileInputStream(srcFile);//创建输入流，然后转压缩文件输入流
            zipArchiveInputStream = new ZipArchiveInputStream(inputStream, encoding);
            //遍历解压每一个文件.
            while (null != (archiveEntry = zipArchiveInputStream.getNextEntry())) {
                String archiveEntryFileName = archiveEntry.getName();//获取文件名
                File entryFile = new File(descDir, archiveEntryFileName);//把解压出来的文件写到指定路径
                if (archiveEntry.isDirectory()) {
                    entryFile.mkdirs();
                    continue;
                }
                if (!entryFile.getParentFile().exists()) {
                    entryFile.getParentFile().mkdirs();
                }
                byte[] buffer = new byte[1024 * 5];
                outputStream = new FileOutputStream(entryFile);
                int length = -1;
                while ((length = zipArchiveInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e) {
            throw e;
        } finally {
            try {
                if (null != outputStream) {
                    outputStream.close();
                }
                if (null != zipArchiveInputStream) {
                    zipArchiveInputStream.close();
                }
                if (null != inputStream) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
