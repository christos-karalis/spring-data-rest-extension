package net.sourceforge.extension.controller;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Created by christos.karalis on 6/6/2017.
 */
public class DefaultMultipartFileProcessor implements MultipartFileProcessor<byte[]> {

    public byte[] persistMultipartFile(MultipartFile multipartFile) throws IOException {
        return multipartFile.getBytes();
    }

}
