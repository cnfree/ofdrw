package org.ofdrw.crypto;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.jetbrains.annotations.NotNull;
import org.ofdrw.core.crypto.encryt.EncryptEntries;
import org.ofdrw.pkg.container.OFDDir;
import org.ofdrw.reader.ZipUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OFD加密器
 *
 * @author 权观宇
 * @since 2021-07-13 18:10:12
 */
public class OFDEncryptor implements Closeable {

//    /**
//     * OFD虚拟容器对象
//     */
//    private final OFDDir ofdDir;

    /**
     * 加密后文件输出位置
     */
    private Path out;

    /**
     * 工作过程中的工作目录
     * <p>
     * 用于存放解压后的OFD文档容器内容
     */
    private Path workDir;

    /**
     * 是否已经关闭
     */
    private boolean closed;

    /**
     * 随机源
     * <p>
     * 默认使用软件随机源
     */
    private SecureRandom random;

    /**
     * CBC模式分块加密，填充模式PKCS#7
     */
    private PaddedBufferedBlockCipher blockCipher;

    /**
     * 用户提供的加密器列表
     */
    private List<UserFEKEncryptor> userEncryptorList;

    /**
     * 容器文件过滤器
     * <p>
     * 用于判断文件是否需要被加密，返回false表示不需要加密
     */
    private ContainerFileFilter cfFilter;

    private OFDEncryptor() {
    }

    /**
     * 创建OFD加密器
     *
     * @param ofdFile 待加密的OFD文件路径
     * @param out     加密后的OFD路径
     * @throws IOException IO操作异常
     */
    public OFDEncryptor(@NotNull Path ofdFile, @NotNull Path out) throws IOException {
        if (ofdFile == null || Files.notExists(ofdFile)) {
            throw new IllegalArgumentException("文件位置(ofdFile)不正确");
        }
        if (out == null) {
            throw new IllegalArgumentException("加密后文件位置(out)为空");
        }
        this.out = out;
        this.workDir = Files.createTempDirectory("ofd-tmp-");
        // 解压文档，到临时的工作目录
        ZipUtil.unZipFiles(ofdFile.toFile(), this.workDir.toAbsolutePath() + File.separator);
        this.userEncryptorList = new ArrayList<>(3);
//        this.ofdDir = new OFDDir(workDir);
        this.random = new SecureRandom();
        this.blockCipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()), new PKCS7Padding());
    }

    /**
     * 添加加密用户
     *
     * @param encryptor 加密用户的加密器
     * @return this
     */
    public OFDEncryptor addUser(@NotNull UserFEKEncryptor encryptor) {
        if (encryptor == null) {
            return this;
        }
        this.userEncryptorList.add(encryptor);
        return this;
    }

    /**
     * 设置随机源
     *
     * @param random 随机源
     * @return this
     */
    public OFDEncryptor setRandom(@NotNull SecureRandom random) {
        if (random == null) {
            return this;
        }
        this.random = random;
        return this;
    }

    /**
     * 设置 容器文件过滤器
     * <p>
     * 该过滤器用于决定哪些文件将会被加密
     * <p>
     * 过滤器结果为false 那么该文件将不会被加密
     *
     * @param filter 过滤器
     * @return this
     */
    public OFDEncryptor setContainerFileFilter(ContainerFileFilter filter) {
        if (filter == null) {
            return this;
        }
        this.cfFilter = filter;
        return this;
    }

    /**
     * 执行加密
     *
     * @return this
     * @throws IOException     加密
     * @throws CryptoException 加密异常
     */
    public OFDEncryptor encrypt() throws IOException, CryptoException {
        if (this.userEncryptorList.isEmpty()) {
            throw new IllegalArgumentException("没有可用加密用户(UserFEKEncryptor)");
        }

        if (!Files.exists(this.out)) {
            Files.createDirectories(this.out.getParent());
        }
        // 待加密文件列表
        final List<ContainerPath> tbEncArr = this.getToBeEncFiles();
        // 加密块大小
        int blockSize = blockCipher.getBlockSize();
        byte[] fek = new byte[blockSize];
        byte[] iv = new byte[blockSize];
        // 生成文件加密密钥和IV
        random.nextBytes(fek);
        random.nextBytes(iv);
        ParametersWithIV keyParam = new ParametersWithIV(new KeyParameter(fek), iv);
        final EncryptEntries encryptEntries = this.encryptFiles(tbEncArr, keyParam);
        // TODO: 创建或读取加密入口文件
        // 执行打包程序
        new OFDDir(workDir.toAbsolutePath()).jar(out);
        return this;
    }

    /**
     * 获取待加密文件列表
     * <p>
     * 执行过滤器过滤文件
     *
     * @return 待加密文件列表
     * @throws IOException IO读写异常
     */
    private List<ContainerPath> getToBeEncFiles() throws IOException {
        return Files.walk(this.workDir)
                // 移除目录文件
                .filter(Files::isRegularFile)
                // 通过过滤器过滤加密得到文件
                .map((path) -> {
                    String cfPath = path.toAbsolutePath().toString()
                            .replace(this.workDir.toAbsolutePath().toString(), "")
                            .replace("\\", "/");
                    return new ContainerPath(cfPath, path);
                })
                .filter((cf) -> {
                    if (this.cfFilter == null) {
                        return true;
                    }
                    // 过滤
                    return this.cfFilter.filter(cf.getPath(), cf.getAbs());
                }).collect(Collectors.toList());
    }

    /**
     * 加密文件
     *
     * @param tbEncArr 待加密文件列表
     * @param keyParam 加密密钥参数
     * @return 明密文映射表
     * @throws IOException                文件读写异常
     * @throws InvalidCipherTextException 加密过程中异常
     */
    private EncryptEntries encryptFiles(List<ContainerPath> tbEncArr, ParametersWithIV keyParam) throws IOException, InvalidCipherTextException {
        EncryptEntries entriesMap = new EncryptEntries();
        byte[] buffIn = new byte[4096];
        byte[] buffOut = new byte[4096 + this.blockCipher.getBlockSize()];
        int len = 0;
        // 创建明密文映射表
        for (ContainerPath plaintextCp : tbEncArr) {
            int bytesProcessed = 0;
            // 创建加密后的文件
            final ContainerPath encryptedCp = plaintextCp.createEncryptedFile();
            try (InputStream in = Files.newInputStream(plaintextCp.getAbs());
                 OutputStream out = Files.newOutputStream(encryptedCp.getAbs())) {
                this.blockCipher.init(true, keyParam);
                while ((len = in.read(buffIn)) != -1) {
                    // 分块加密
                    bytesProcessed = this.blockCipher.processBytes(buffIn, 0, len, buffOut, 0);
                    if (bytesProcessed > 0) {
                        out.write(buffOut, 0, bytesProcessed);
                    }
                }
                // 执行最后一个分块的加密和填充
                bytesProcessed = this.blockCipher.doFinal(buffOut, 0);
                out.write(buffOut, 0, bytesProcessed);
                this.blockCipher.reset();
            }
            // 加密完成后，删除源文件
            Files.delete(plaintextCp.getAbs());
            // 添加明密文映射表的映射关系
            entriesMap.addEncryptEntry(plaintextCp.getPath(), encryptedCp.getPath());
        }
        return entriesMap;
    }


    /**
     * 关闭文档
     * <p>
     * 删除工作区
     *
     * @throws IOException 工作区删除异常
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (workDir != null && Files.exists(workDir)) {
            try {
                FileUtils.deleteDirectory(workDir.toFile());
            } catch (IOException e) {
                throw new IOException("无法删除Reader的工作空间，原因：" + e.getMessage(), e);
            }
        }
    }
}
