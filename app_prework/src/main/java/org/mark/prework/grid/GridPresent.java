package org.mark.prework.grid;

import org.mark.base.FileUtils;
import org.mark.prework.GridActivity;
import org.mark.prework.TfFileUtils;
import org.mark.prework.db.DbMock;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Mark on 2018/10/24
 */
public class GridPresent {

    private GridActivity activity;

    public GridPresent(GridActivity activity) {
        this.activity = activity;
    }


    public void loadImages() {
        activity.updateAdapter();
    }

    public void moveToDeleteFolderWithChoiceList(List<TfFileUtils.ImageAcc> paths) {
        for (TfFileUtils.ImageAcc acc : paths) {
            String path = acc.getPath();
            File file = new File(path);

            String parent = new File(file.getParent()).getParent();

            String deletePath = parent + File.separator + "delete";

            try {
                File targetFolder = new File(deletePath);
                if (!targetFolder.isDirectory()) {
                    targetFolder.mkdir();
                }
                FileUtils.moveToTargetWithRandomName(targetFolder, file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void moveToTarget(final List<TfFileUtils.ImageAcc> paths, final File target) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (TfFileUtils.ImageAcc acc : paths) {
                    String path = acc.getPath();
                    File file = new File(path);

                    try {
                        FileUtils.moveToTargetWithRandomName(target, file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }


    public void move() {
    }

    public void hide(String text) {
        List<TfFileUtils.ImageAcc> list = DbMock.getInstance().getImages();
        Iterator<TfFileUtils.ImageAcc> imageAccIterator = list.iterator();

        while (imageAccIterator.hasNext()) {
            TfFileUtils.ImageAcc acc = imageAccIterator.next();

            if (acc.same(text)) {
                imageAccIterator.remove();
            }
        }

        activity.updateAdapter();
    }
}
