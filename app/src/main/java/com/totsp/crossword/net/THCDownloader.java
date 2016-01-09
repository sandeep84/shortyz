package com.totsp.crossword.net;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.totsp.crossword.io.IO;
import com.totsp.crossword.puz.Box;
import com.totsp.crossword.puz.Puzzle;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;



/**
 * The Hindu Crossword Puzzles
 * URL: http://www.thehindu.com/
 */
public class THCDownloader extends AbstractDownloader {
	private static String NAME = "The Hindu Crossword"; 
    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
    NumberFormat nf = NumberFormat.getInstance();
    
    private static final int NUM_CELLS = 15;
    private static final int GREY_THRESHOLD = 0xf0;

    private Box[][] boxes = new Box[NUM_CELLS][NUM_CELLS];
    private String author = "";
    private String title = "";
    private int numClues = 0;
    ArrayList<String> acrossClues = new ArrayList<String>();
    ArrayList<String> downClues = new ArrayList<String>();
    
    public THCDownloader() {
        super("http://www.thehindu.com/todays-paper/tp-index/", DOWNLOAD_DIR, NAME);
        nf.setMinimumIntegerDigits(2);
        nf.setMaximumFractionDigits(0);
    }

    public int[] getDownloadDates() {
		return DATE_NO_SUNDAY;
    }

    public String getName() {
        return NAME;
    }

	public String getContent(String url) throws IOException {
		URL u = new URL(url);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		IO.copyStream(u.openStream(), baos);

		return new String(baos.toByteArray());
	}

    public File download(Date date) {
        File downloadTo = new File(this.downloadDirectory, this.createFileName(date));

        if (downloadTo.exists()) {
            return null;
        }

        if (!downloadAndParseGrid(date)) {
        	return null;
        }

		try {
	        Puzzle puz = new Puzzle();
	        puz.setAuthor(author);
	        puz.setTitle(title);
	        puz.setDate(date);
	        puz.setWidth(NUM_CELLS);
	        puz.setHeight(NUM_CELLS);
	        puz.setBoxes(boxes);
	        puz.setVersion(IO.VERSION_STRING);
	        puz.setUpdatable(true);
	        puz.setNotes("");
	        puz.setCopyright("Copyright " + date.getYear() + ", The Hindu");
	        
	        //Clean up clues that start off with the clue-number and create a list of rawClues
	        boxes = puz.getBoxes();
        	int rawClueIdx = 0;
        	int acClueIdx = 0;
        	int dnClueIdx = 0;
        	String[] rawClues = new String[numClues];
        	
    		for (int j=0; j<NUM_CELLS; j++) {
            	for (int i=0; i<NUM_CELLS; i++) {
        			if (boxes[j][i] != null) {
                        boxes[j][i].setResponder("");
        				String pattern = "^-?" + boxes[j][i].getClueNumber() + "(.*)";
	        			if (boxes[j][i].isAcross()) {
	        				rawClues[rawClueIdx] = this.acrossClues.get(acClueIdx++);
	        				rawClues[rawClueIdx] = rawClues[rawClueIdx].replaceAll(pattern, "$1");
	        				rawClueIdx++;
	        			}
	        			if (boxes[j][i].isDown()) {
	        				rawClues[rawClueIdx] = this.downClues.get(dnClueIdx++);
	        				rawClues[rawClueIdx] = rawClues[rawClueIdx].replaceAll(pattern, "$1");
	        				rawClueIdx++;
	        			}
        			}
        		}
        	}
        	puz.setRawClues(rawClues);
	        puz.setNumberOfClues(rawClueIdx);

			IO.save(puz, downloadTo);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
            return null;
		} catch (IOException e) {
			e.printStackTrace();
            return null;
		}

        return downloadTo;
    }

    @Override
    protected String createUrlSuffix(Date date) {
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		try {
			String html_string = this.getContent(this.baseUrl + "?date=" + sdf.format(date));
			
            Pattern p = Pattern.compile("MISCELLANEOUS.*<a\\s*href=\"(.*?)\"\\s*>The Hindu Crossword.*</a>", Pattern.MULTILINE | Pattern.DOTALL);
            Matcher m = p.matcher(html_string);
            if (m.find()) {
            	return m.group(1);
            }
		} catch (IOException e) {
			e.printStackTrace();
		}
        return null;
    }
    
    private boolean parseGridImage(String imageLink) {
    	if (imageLink.equals("")) {
            LOG.log(Level.SEVERE, "Unable to identify the grid image.");
            return false;
    	} else {
    		URL u;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try {
				u = new URL(imageLink);
	            IO.copyStream(u.openStream(), baos);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            Bitmap grid = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size());
            
            int width = grid.getWidth();
            int height = grid.getHeight();	            
            
            int start_x = 0; int end_x = 0;
            int start_y = 0; int end_y = 0;
            
            boolean first = true;
            
            for (int x=0; x<width; x++) {
            	int c = grid.getPixel(x, height/2) & 0xff;
            	if (c < THCDownloader.GREY_THRESHOLD) {
            		if (first) { start_x = x; first = false; }
            		end_x = x;
            	}
            }
            first = true;
            for (int y=0; y<height; y++) {
            	int c = grid.getPixel(width/2, y) & 0xff;
            	if (c < THCDownloader.GREY_THRESHOLD) {
            		if (first) { start_y = y; first = false; }
            		end_y = y;
            	}
            }
            
            float cell_width;
            cell_width = (end_x - start_x) / (float)THCDownloader.NUM_CELLS;
            cell_width = (cell_width + ((end_y - start_y) / (float)THCDownloader.NUM_CELLS)) / 2;

            for (float j=start_y+cell_width/2; j<start_y+cell_width*THCDownloader.NUM_CELLS; j+=cell_width) {
                for (float i=start_x+cell_width/2; i<start_x+cell_width*THCDownloader.NUM_CELLS; i+=cell_width) {
                    int x = (int) i;
                    int y = (int) j;
                    int diag_x = (int)((end_x + start_x) - i);
                    int diag_y = (int)((end_y + start_y) - j);

                    int avg = ((grid.getPixel(x, y)&0xff)     			+ (grid.getPixel(x+1, y)&0xff) + 
                    		   (grid.getPixel(x, y+1)&0xff)   			+ (grid.getPixel(x+1, y+1)&0xff) +
                    		   (grid.getPixel(diag_x, diag_y)&0xff)     + (grid.getPixel(diag_x+1, diag_y)&0xff) +
                    		   (grid.getPixel(diag_x, diag_y+1)&0xff)   + (grid.getPixel(diag_x+1, diag_y+1)&0xff)
                    		  ) / 8;
                    
                    x = (int) ((i-start_x)/cell_width);
                    y = (int) ((j-start_y)/cell_width);
                    if (avg > THCDownloader.GREY_THRESHOLD) {
                    	boxes[y][x] = new Box();
                    	boxes[y][x].setSolution('X');
                    	boxes[y][x].setResponse(' ');
                    	System.out.print('X');
                    } else {
                    	boxes[y][x] = null;
                    	System.out.print('.');
                    }
                }
                System.out.println();
            }
    	}
    	return true;
    }

    private boolean downloadAndParseGrid(Date date) {
    	String imageLink = "";
    	
        boolean inAcross = false; 
        boolean inDown = false;
        
        try {
        	String html_string = this.getContent(this.createUrlSuffix(date));

			Document doc = Jsoup.parse(html_string);

            Elements authors = doc.select("meta[name=author]");
            this.author = authors.first().attr("content");

            Elements titles = doc.select("meta[property=og:title]");
            this.title = titles.first().attr("content");

            Elements picCarousel = doc.getElementsByClass("jCarouselHolder");
            Elements picLinks = picCarousel.select("img[src$=.jpg]");
            imageLink = picLinks.first().attr("src");
            parseGridImage(imageLink);

            //Now, the clues
            numClues = 0;
            Elements clueDiv = doc.getElementsByClass("article-text");
            Elements clueElements   = clueDiv.select("p[class=body]");

            for (Element clueElement : clueElements)
            {
            	String clue = android.text.Html.fromHtml(clueElement.text()).toString();
        		if (clue.contains("Across")) {
        			inAcross = true; inDown = false;
        		} else if (clue.contains("Down")) {
        			inDown = true; inAcross = false;
        		} else if ((inAcross || inDown) && (!clue.equals(""))) {
        			if (inAcross) {
        				acrossClues.add(clue);
        			} else if (inDown) {
        				downClues.add(clue);
        			}
	        		numClues++;
        		}
    		}
            

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
}
