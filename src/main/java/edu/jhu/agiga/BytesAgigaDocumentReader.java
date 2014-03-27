package edu.jhu.agiga;

public class BytesAgigaDocumentReader extends AgigaDocumentReader {

    /** Gives public access to the package private constructor. */
    public BytesAgigaDocumentReader(byte[] b, AgigaPrefs prefs) {
        super(b, prefs);
    }

}
