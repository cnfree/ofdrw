import org.ofdrw.converter.ConvertHelper;
import org.ofdrw.converter.GeneralConvertException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Test {
    public static void main(String[] args) throws IOException {
//        Library library = FreeType.newLibrary();
//        /* --- Create face from .TTF --- */
//        File[] fonts = new File("D:\\Res").listFiles();
//        for (File font : fonts) {
//            Face face = library.newFace("D:\\develop\\ofdrw\\ofdrw-converter\\src\\test\\resources\\zsbk\\Doc_0\\Res\\font_766.ttf", 0);
//            face.loadGlyph(1922, FreeTypeConstants.FT_LOAD_RENDER);
//        }

        // 1. 文件输入路径
        File[] children = new File("D:\\develop\\ofdrw-fork\\ofdrw-converter\\src\\test\\resources").listFiles(t -> t.isFile() && t.getName().indexOf(".ofd")!=-1);
        for (File child : children) {
            Path src = Paths.get(child.getAbsolutePath());
            // 2. 转换后文件输出位置
            Path dst = Paths.get(child.getAbsolutePath().replace(".ofd", ".pdf"));
            try {
                // 3. OFD转换PDF
                ConvertHelper.toPdf(src, dst);
                System.out.println("生成文档位置: " + dst.toAbsolutePath());
            } catch (GeneralConvertException e) {
                // GeneralConvertException 类型错误表明转换过程中发生异常
                e.printStackTrace();
            }
        }
    }
}
