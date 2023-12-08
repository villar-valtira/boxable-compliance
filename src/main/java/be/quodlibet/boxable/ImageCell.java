package be.quodlibet.boxable;

import org.apache.pdfbox.pdmodel.PDPage;

import be.quodlibet.boxable.image.Image;

public class ImageCell<T extends PDPage> extends Cell<T> {

	private Image img;
	
	private final HorizontalAlignment align;
	
	private final VerticalAlignment valign;

	private String alternateText;
	private HorizontalAlignment textAlign;
	private float textMarginFromImage;
	private boolean removeEmptyLines;

	ImageCell(Row<T> row, float width, Image image, boolean isCalculated) {
		super(row, width, null, isCalculated);
		this.img = image;
		if(image.getWidth() > getInnerWidth()){
			scaleToFit();
		}
		this.align = HorizontalAlignment.LEFT;
		this.valign = VerticalAlignment.TOP;

		this.textMarginFromImage = 10;
		this.removeEmptyLines = false;
	}

	public void scaleToFit() {
		img = img.scale(getInnerWidth());
	}

	ImageCell(Row<T> row, float width, Image image, boolean isCalculated, HorizontalAlignment align,
			VerticalAlignment valign) {
		super(row, width, null, isCalculated, align, valign);
		this.img = image;
		if(image.getWidth() > getInnerWidth()){
			scaleToFit();
		}
		this.align = align;
		this.valign = valign;

		this.textMarginFromImage = 10;
	}

	@Override
	public float getTextHeight() {
		float height = img.getHeight();
		if (!this.getText().isEmpty()) {
			height += this.getParagraph().getHeight();
			height += this.textMarginFromImage;
		}

		return height;
	}

	@Override
	public float getHorizontalFreeSpace() {
		return getInnerWidth() - img.getWidth();
	}
	
	@Override
	public float getVerticalFreeSpace() {
		if (!this.getText().isEmpty()) {
			return getInnerHeight() - img.getHeight() - this.getParagraph().getHeight() - this.textMarginFromImage;
		} else {
			return getInnerHeight() - img.getHeight();
		}
	}


	/**
	 * <p>
	 * Method which retrieve {@link Image}
	 * </p>
	 * 
	 * @return {@link Image}
	 */
	public Image getImage() {
		return img;
	}

	public String getAlternateText() {
		return alternateText;
	}

	public void setAlternateText(String alternateText) {
		this.alternateText = alternateText;
	}


	public HorizontalAlignment getTextAlign() {
		return textAlign;
	}

	public void setTextAlign(HorizontalAlignment textAlign) {
		this.textAlign = textAlign;
	}

	public float getTextMarginFromImage() {
		return textMarginFromImage;
	}

	public void setTextMarginFromImage(float textMarginFromImage) {
		this.textMarginFromImage = textMarginFromImage;
	}

	public boolean removeEmptyLines() {
		return removeEmptyLines;
	}

	public void setRemoveEmptyLines(boolean removeEmptyLines) {
		this.removeEmptyLines = removeEmptyLines;
	}
}
