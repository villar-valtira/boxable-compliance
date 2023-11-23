package be.quodlibet.boxable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import be.quodlibet.boxable.image.Image;
import be.quodlibet.boxable.utils.PageContentStreamOptimized;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.quodlibet.boxable.text.Token;
import be.quodlibet.boxable.utils.FontUtils;
import be.quodlibet.boxable.utils.PDStreamUtils;

import javax.imageio.ImageIO;

public class TableCell<T extends PDPage> extends Cell<T> {

	private final static Logger logger = LoggerFactory.getLogger(TableCell.class);

	private final String tableData;
	private final float width;
	private float yStart;
	private float xStart;
	private float height = 0;
	private final PDDocument doc;
	private final PDPage page;
	private final float marginBetweenElementsY = FontUtils.getHeight(getFont(), getFontSize());
	private final HorizontalAlignment align;
	private final VerticalAlignment valign;

	private PageContentStreamOptimized tableCellContentStream;

	// page margins
	private final float pageTopMargin;
	private final float pageBottomMargin;
	// default title fonts
	private final int tableTitleFontSize = 8;

	TableCell(Row<T> row, float width, String tableData, boolean isCalculated, PDDocument document, PDPage page,
			  float yStart, float pageTopMargin, float pageBottomMargin) {
		this(row, width, tableData, isCalculated, document, page, yStart, pageTopMargin, pageBottomMargin,
				HorizontalAlignment.LEFT, VerticalAlignment.TOP);
	}

	TableCell(Row<T> row, float width, String tableData, boolean isCalculated, PDDocument document, PDPage page,
			  float yStart, float pageTopMargin, float pageBottomMargin, final HorizontalAlignment align,
			  final VerticalAlignment valign) {
		super(row, width, tableData, isCalculated);
		this.tableData = tableData;
		this.width = width * row.getWidth() / 100;
		this.doc = document;
		this.page = page;
		this.yStart = yStart;
		this.pageTopMargin = pageTopMargin;
		this.pageBottomMargin = pageBottomMargin;
		this.align = align;
		this.valign = valign;
		fillTable();
	}

	/**
	 * <p>
	 * This method just fills up the table's with her content for proper table
	 * cell height calculation. Position of the table (x,y) is not relevant
	 * here.
	 * </p>
	 * <p>
	 * NOTE: if entire row is not header row then use bold instead header cell (
	 * {@code
	 *
	<th>})
	 * </p>
	 */
	@SuppressWarnings({ "unused"})
	public void fillTable() {
		try {
			// please consider the cell's paddings
			float tableWidth = this.width - getLeftPadding() - getRightPadding();
			tableCellContentStream = new PageContentStreamOptimized(new PDPageContentStream(doc, page, true, true));
			// check if there is some additional text outside inner table
			String[] outerTableText = tableData.split("<table");
			// don't forget to attach split tag
			for (int i = 1; i < outerTableText.length; i++) {
				outerTableText[i] = "<table " + outerTableText[i];
			}
			Paragraph outerTextParagraph;
			String caption;
			height = 0;
			height = (getTopBorder() == null ? 0 : getTopBorder().getWidth()) + getTopPadding();
			for (String element : outerTableText) {
				if (element.contains("</table")) {
					String[] chunks = element.split("</table>");
					for (String chunkie : chunks) {
						if (chunkie.contains("<table")) {
							// table title
							Document document = Jsoup.parse(chunkie);
							Element captionTag = document.select("caption").first();
							Paragraph tableTitle = null;
							if (captionTag != null) {
								caption = captionTag.text();
								tableTitle = new Paragraph(caption, getFontBold(), tableTitleFontSize, tableWidth,
										HorizontalAlignment.CENTER, null);
								yStart -= tableTitle.getHeight() + marginBetweenElementsY;
							}
							height += (captionTag != null ? tableTitle.getHeight() + marginBetweenElementsY : 0);
							createInnerTable(tableWidth, document, page, false);
						} else {
							// make paragraph and get tokens
							outerTextParagraph = new Paragraph(chunkie, getFont(), 8, (int) tableWidth);
							outerTextParagraph.getLines();
							height += outerTextParagraph.getHeight() + marginBetweenElementsY;
							yStart = writeOrCalculateParagraph(outerTextParagraph, true);
						}
					}
				} else {
					// make paragraph and get tokens
					outerTextParagraph = new Paragraph(element, getFont(), 8, (int) tableWidth);
					outerTextParagraph.getLines();
					height += outerTextParagraph.getHeight() + marginBetweenElementsY;
					yStart = writeOrCalculateParagraph(outerTextParagraph, true);
				}
			}
			tableCellContentStream.close();
		} catch (IOException e) {
			logger.warn("Cannot create table in TableCell. Table data: '{}'", tableData + e);
		}
	}

	private void createInnerTable(float tableWidth, Document document, PDPage currentPage, boolean drawTable) throws IOException {

		BaseTable table = new BaseTable(yStart, PDRectangle.A4.getHeight() - pageTopMargin, pageTopMargin,
				pageBottomMargin, tableWidth, xStart, doc, currentPage, true, true);
		document.outputSettings().prettyPrint(false);
		Element htmlTable = document.select("table").first();

        assert htmlTable != null;
        Elements rows = htmlTable.select("tr");
		for (Element htmlTableRow : rows) {
			Row<PDPage> row = table.createRow(0);
			Elements tableCols = htmlTableRow.select("td");
			Elements tableHeaderCols = htmlTableRow.select("th");
			// do we have header columns?
			boolean tableHasHeaderColumns = !tableHeaderCols.isEmpty();
			if (tableHasHeaderColumns) {
				// if entire row is not header row then use bold instead
				// header cell (<th>)
				row.setHeaderRow(true);
			}
			int columnsSize = tableHasHeaderColumns ? tableHeaderCols.size() : tableCols.size();
			// calculate how much really columns do you have (including
			// colspans!)
			for (Element col : tableHasHeaderColumns ? tableHeaderCols : tableCols) {
                col.attr("colspan");
                if (!col.attr("colspan").isEmpty()) {
					columnsSize += Integer.parseInt(col.attr("colspan")) - 1;
				}
			}
			for (Element col : tableHasHeaderColumns ? tableHeaderCols : tableCols) {
                col.attr("colspan");
                if (!col.attr("colspan").isEmpty()) {
					row.createCell(
							tableWidth / columnsSize * Integer.parseInt(col.attr("colspan")) / row.getWidth() * 100,
							col.html().replace("&amp;", "&"));
				} else if (col.hasAttr("img")){
					File imageFile = new File(col.attr("src"));
					Image image = new Image(ImageIO.read(imageFile));
					String widthScale = col.attr("width");
					if(!col.attr("width").isEmpty()) {
						image = image.scaleByWidth(Float.parseFloat(col.attr("width")));
					}
					row.createImageCell(Float.parseFloat(widthScale), image);
				}
				else {
					row.createCell(tableWidth / columnsSize / row.getWidth() * 100,
							col.html().replace("&amp;", "&"));
				}
			}
			yStart -= row.getHeight();
		}
		if (drawTable) {
			table.draw();
		}

		height += table.getHeaderAndDataHeight() + marginBetweenElementsY;
	}

	/**
	 * <p>
	 * Method provides writing or height calculation of possible outer text
	 * </p>
	 *
	 * @param paragraph
	 *            Paragraph that needs to be written or whose height needs to be
	 *            calculated
	 * @param onlyCalculateHeight
	 *            if <code>true</code> the given paragraph will not be drawn
	 *            just his height will be calculated.
	 * @return Y position after calculating/writing given paragraph
	 */
	private float writeOrCalculateParagraph(Paragraph paragraph, boolean onlyCalculateHeight) throws IOException {
		int boldCounter = 0;
		int italicCounter = 0;

		if (!onlyCalculateHeight) {
			tableCellContentStream.setRotated(isTextRotated());
		}

		// position at top of current cell descending by font height - font
		// descent, because we are positioning the base line here
		float cursorY = yStart - getTopPadding() - FontUtils.getHeight(getFont(), getFontSize())
				- FontUtils.getDescent(getFont(), getFontSize()) - (getTopBorder() == null ? 0 : getTopBorder().getWidth());
		float cursorX = xStart;

		// loop through tokens
		for (Map.Entry<Integer, List<Token>> entry : paragraph.getMapLineTokens().entrySet()) {

			// calculate the width of this line
			float freeSpaceWithinLine = paragraph.getMaxLineWidth() - paragraph.getLineWidth(entry.getKey());
			if (isTextRotated()) {
				switch (align) {
					case CENTER:
						cursorY += freeSpaceWithinLine / 2;
						break;
					case LEFT:
						break;
					case RIGHT:
						cursorY += freeSpaceWithinLine;
						break;
				}
			} else {
				switch (align) {
					case CENTER:
						cursorX += freeSpaceWithinLine / 2;
						break;
					case LEFT:
						// it doesn't matter because X position is always the same
						// as row above
						break;
					case RIGHT:
						cursorX += freeSpaceWithinLine;
						break;
				}
			}

			// iterate through tokens in current line
			PDFont currentFont = paragraph.getFont(false, false);
			for (Token token : entry.getValue()) {
				switch (token.getType()) {
					case OPEN_TAG:
						if ("b".equals(token.getData())) {
							boldCounter++;
						} else if ("i".equals(token.getData())) {
							italicCounter++;
						}
						break;
					case CLOSE_TAG:
						if ("b".equals(token.getData())) {
							boldCounter = Math.max(boldCounter - 1, 0);
						} else if ("i".equals(token.getData())) {
							italicCounter = Math.max(italicCounter - 1, 0);
						}
						break;
					case PADDING:
						cursorX += Float.parseFloat(token.getData());
						break;
					case ORDERING, TEXT:
						currentFont = paragraph.getFont(boldCounter > 0, italicCounter > 0);
						tableCellContentStream.setFont(currentFont, getFontSize());
						if (isTextRotated()) {
							// if it is not calculation then draw it
							if (!onlyCalculateHeight) {
								tableCellContentStream.newLineAt(cursorX, cursorY);
								tableCellContentStream.showText(token.getData());
							}
							cursorY += token.getWidth(currentFont) / 1000 * getFontSize();
						} else {
							// if it is not calculation then draw it
							if (!onlyCalculateHeight) {
								tableCellContentStream.newLineAt(cursorX, cursorY);
								tableCellContentStream.showText(token.getData());
							}
							cursorX += token.getWidth(currentFont) / 1000 * getFontSize();
						}
						break;
					case BULLET:
						float widthOfSpace = currentFont.getSpaceWidth();
						float halfHeight = FontUtils.getHeight(currentFont, getFontSize()) / 2;
						if (isTextRotated()) {
							if (!onlyCalculateHeight) {
								PDStreamUtils.rect(tableCellContentStream, cursorX + halfHeight, cursorY,
										token.getWidth(currentFont) / 1000 * getFontSize(),
										widthOfSpace / 1000 * getFontSize(), getTextColor());
							}
							// move cursorY for two characters (one for bullet, one
							// for space after bullet)
							cursorY += 2 * widthOfSpace / 1000 * getFontSize();
						} else {
							if (!onlyCalculateHeight) {
								PDStreamUtils.rect(tableCellContentStream, cursorX, cursorY + halfHeight,
										token.getWidth(currentFont) / 1000 * getFontSize(),
										widthOfSpace / 1000 * getFontSize(), getTextColor());
							}
							// move cursorX for two characters (one for bullet, one
							// for space after bullet)
							cursorX += 2 * widthOfSpace / 1000 * getFontSize();
						}
						break;
                }
			}
			// reset
			cursorX = xStart;
			cursorY -= FontUtils.getHeight(getFont(), getFontSize());
		}
		return cursorY;
	}

	/**
	 * <p>
	 * This method draw table cell with proper X,Y position which are determined
	 * in {@link Table#draw()} method
	 * </p>
	 * <p>
	 * NOTE: if entire row is not header row then use bold instead header cell (
	 * {@code
	 *
	<th>})
	 * </p>
	 *
	 * @param page
	 *            {@link PDPage} where table cell be written on
	 *
	 */
	@SuppressWarnings({ "unused"})
	public void draw(PDPage page) {
		try {
			// please consider the cell's paddings
			float tableWidth = this.width - getLeftPadding() - getRightPadding();
			tableCellContentStream = new PageContentStreamOptimized(new PDPageContentStream(doc, page, true, true));
			// check if there is some additional text outside inner table
			String[] outerTableText = tableData.split("<table");
			// don't forget to attach splited tag
			for (int i = 1; i < outerTableText.length; i++) {
				outerTableText[i] = "<table " + outerTableText[i];
			}
			Paragraph outerTextParagraph;
			String caption;
			height = 0;
			height = (getTopBorder() == null ? 0 : getTopBorder().getWidth()) + getTopPadding();
			for (String element : outerTableText) {
				if (element.contains("</table")) {
					String[] chunks = element.split("</table>");
					for (String chunkie : chunks) {
						if (chunkie.contains("<table")) {
							// table title
							Document document = Jsoup.parse(chunkie);
							Element captionTag = document.select("caption").first();
							Paragraph tableTitle = null;
							if (captionTag != null) {
								caption = captionTag.text();
								tableTitle = new Paragraph(caption, getFontBold(), tableTitleFontSize, tableWidth,
										HorizontalAlignment.CENTER, null);
								yStart = tableTitle.write(tableCellContentStream, xStart, yStart)
										- marginBetweenElementsY;
							}
							height += (captionTag != null ? tableTitle.getHeight() + marginBetweenElementsY : 0);
							createInnerTable(tableWidth, document, page, true);
						} else {
							// make paragraph and get tokens
							outerTextParagraph = new Paragraph(chunkie, getFont(), 8, (int) tableWidth);
							outerTextParagraph.getLines();
							height += outerTextParagraph.getHeight() + marginBetweenElementsY;
							yStart = writeOrCalculateParagraph(outerTextParagraph, false);
						}
					}
				} else {
					// make paragraph and get tokens
					outerTextParagraph = new Paragraph(element, getFont(), 8, (int) tableWidth);
					outerTextParagraph.getLines();
					height += outerTextParagraph.getHeight() + marginBetweenElementsY;
					yStart = writeOrCalculateParagraph(outerTextParagraph, false);
				}
			}
			tableCellContentStream.close();
		} catch (IOException e) {
			logger.warn("Cannot draw table for TableCell! Table data: '{}'", tableData + e);
		}
	}

	public float getXPosition() {
		return xStart;
	}

	public void setXPosition(float xStart) {
		this.xStart = xStart;
	}

	public float getYPosition() {
		return yStart;
	}

	public void setYPosition(float yStart) {
		this.yStart = yStart;
	}

	@Override
	public float getTextHeight() {
		return height;
	}

	@Override
	public float getHorizontalFreeSpace() {
		return getInnerWidth() - width;
	}

	@Override
	public float getVerticalFreeSpace() {
		return getInnerHeight() - width;
	}

	public static void main(String[] args) {
		try {
			PDDocument doc = new PDDocument();
			PDPage page = new PDPage();
			doc.addPage(page);

			float margin = 34;

			float yStartNewPage = page.getMediaBox().getHeight() - (2 * margin);
			float tableWidth = 540;

			boolean drawContent = true;
			float yStart = yStartNewPage;
			float bottomMargin = 0;
			float yPosition = 284;

			BaseTable table = new BaseTable(yPosition, yStartNewPage,
					bottomMargin, tableWidth, margin, doc, page, true, drawContent);

			String table1 = "<table><tr><td><img src=\"https://mos-asset-upload-stage.s3.amazonaws.com/icons/award_brand_blue.png\" alt=\"award_brand_blue\" width=\"30\" height=\"30\" /></td><td>First row, second value</td></tr></table>";

			Row<PDPage> row = table.createRow(10);
			row.createTableCell(80, table1, doc, page, yStart, 10, 1);

//			Row<PDPage> row = table.createRow(10);
//			TableCell<PDPage> cell = (TableCell<PDPage>) row.createCell(100, "Data 1");
//			cell = (TableCell<PDPage>) row.createCell(100, "Data 2");
//			cell = (TableCell<PDPage>) row.createCell(100, "Data 3");
//			cell = (TableCell<PDPage>) row.createCell(100, "Data 4");
			table.draw();
			doc.save("/tmp/text.pdf");
			doc.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}