package it.panda.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class CarImagesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void findsFolderIgnoringCase() throws IOException {
        File pictures = tmp.newFolder("Pictures");
        File car = new File(pictures, "fiat_PANDA");
        assertEquals(true, car.mkdir());
        assertEquals(car, CarImages.findFolder(pictures, "FIAT_Panda"));
    }

    @Test
    public void missingFolderIsNull() throws IOException {
        assertNull(CarImages.findFolder(tmp.newFolder("Pictures"), "FIAT_Panda"));
        assertNull(CarImages.findFolder(new File(tmp.getRoot(), "assente"), "X"));
    }

    @Test
    public void findsImageWithAnySupportedExtensionAndCase() throws IOException {
        File dir = tmp.newFolder("car");
        File f = new File(dir, "TOP.JPG");
        assertEquals(true, f.createNewFile());
        assertEquals(f, CarImages.findImage(dir, "Top"));
    }

    @Test
    public void prefersPngWhenSeveralExist() throws IOException {
        File dir = tmp.newFolder("car");
        new File(dir, "Side.jpg").createNewFile();
        File png = new File(dir, "Side.png");
        png.createNewFile();
        assertEquals(png, CarImages.findImage(dir, "Side"));
    }

    @Test
    public void ignoresOtherFilesAndMissingFolder() throws IOException {
        File dir = tmp.newFolder("car");
        new File(dir, "Topography.png").createNewFile();
        new File(dir, "Top.txt").createNewFile();
        assertNull(CarImages.findImage(dir, "Top"));
        assertNull(CarImages.findImage(null, "Top"));
    }

    @Test
    public void sampleSizeKeepsLongSideWithinLimit() {
        assertEquals(1, CarImages.sampleSize(1476, 948, 1600));
        assertEquals(2, CarImages.sampleSize(3000, 2000, 1600));
        assertEquals(4, CarImages.sampleSize(6000, 4000, 1600));
        assertEquals(1, CarImages.sampleSize(0, 0, 1600));
    }
}
