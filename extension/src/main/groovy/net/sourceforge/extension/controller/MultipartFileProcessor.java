package net.sourceforge.extension.controller;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Created by christos.karalis on 6/6/2017.
 */
public interface MultipartFileProcessor<T extends Object> {

    Object persistMultipartFile(MultipartFile multipartFile) throws IOException;

}
