package uz.umar;

/** One part of a multipart/form-data request (from @RequestPart or a MultipartFile param). */
public class MultipartPart {
    public final String name;
    public final boolean isFile;

    public MultipartPart(String name, boolean isFile) {
        this.name = name;
        this.isFile = isFile;
    }
}
