package com.github.greenfinger.ui;

import java.io.File;
import com.github.paganini2008.devtools.io.Directory;
import com.github.paganini2008.devtools.io.DirectoryFilter;
import com.github.paganini2008.devtools.io.FileSearchUtils;
import com.github.paganini2008.devtools.io.FileUtils;

public class TestMain {

    public static void main(String[] args) {
        File directory = new File("C:\\Users\\pagan");
        File[] files = FileSearchUtils.search(directory, new DirectoryFilter() {
            @Override
            public boolean accept(Directory fileInfo) {
                if (fileInfo.getLength() > 50 * FileUtils.MB) {
                    return true;
                }
                return false;
            }
        }, 8, 5);
        for (File file : files) {
            System.out.println(file);
        }

    }

}
