import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

import april.jcam.*;
import april.util.*;
import april.jmat.*;

public class SwingLedTracker
{
    static final int DEFAULT_GRAY_THRESHOLD = 235;
    ImageSource is;

    JFrame jf = new JFrame("LED Tracker Demo");
    JImage jim = new JImage();

    ParameterGUI pg = new ParameterGUI();

    public SwingLedTracker(ImageSource _is)
    {
        is = _is;

        // Determine which slider values we want
        pg.addIntSlider("kthresh","Brightness Threshold",0,255,DEFAULT_GRAY_THRESHOLD);

        jim.setFit(true);

        // Setup window layout
        jf.setLayout(new BorderLayout());
        jf.add(jim, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(1024, 768);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

public boolean fix(int y, int x, int light[][], int last, int w, int h){
	
	if(y == 0) return true;
	if(x == 0) return true;
	if(y == w) return true;
	if(x == h) return true; 

	int current = light[y][x];
	
	if(current != 0) return true;
	
	light[y][x] = last;
	fix(y-1, x, light, last, w, h);
	fix(y, x-1, light, last, w, h);
	fix(y+1, x, light, last, w, h);
	fix(y, x+1, light, last, w, h);

	return true;
}


    // Returns the bounds of the pixel coordinates of the led: {min_x, min_y, max_x, max_y}
    public int[] findLED(BufferedImage img, ParameterGUI pg)
    {
	int avx =0, avy = 0, count = 0;
        //setup
	img = ImageUtil.conformImageToInt(img);
	int data[] = ((DataBufferInt) (img.getRaster().getDataBuffer())).getData();

	//coloring code
	int light[][] = new int[img.getHeight()][img.getWidth()];
	int pixel, location =0, next = 1;
	boolean merge = false;


	for (int y = 1; y < img.getHeight(); y++){
		for (int x = 1; x < img.getWidth(); x++){
			light[y][x] = -1;
		}
	}

	for (int y = 0; y < img.getHeight(); y++){
		for (int x = 0; x < img.getWidth(); x++){

			pixel = ((data[location] >> 16) & 0xFF) + ((data[location] >> 8) & 0xFF) + (data[location] & 0xFF);			
			
			if(pixel > 253*3){
				light[y][x] = 0;
			}
			location ++;
		}
	}

	for (int y = 1; y < img.getHeight(); y++){
		for (int x = 1; x < img.getWidth(); x++){
			
			if(light[y][x] == 0){
				fix(y, x, light, next, img.getHeight(), img.getWidth());
				next++;
			}
		}
	}
	int tsize = 7;
	int stat[][] = new int[next][tsize]; 
	int xstart = 3;
	location = 0;

	for(int i = 0; i < next; i ++){
		stat[i][2] = 0;
		stat[i][5] = 0;
		stat[i][6] = 0;
	}
	for ( int y = 0; y < img.getHeight(); y++){
		for ( int x = 0; x < img.getWidth(); x++){

			location++;
			int group = light[y][x];

			if( group > 0){
				data[location]  = 255 * group * group * group * group;
				group--;
				//update ymax
				if(y > stat[group][0])
					stat[group][0] = y;
				//update ymin
				if(y < stat[group][1])
					stat[group][1] = y;
				//y total
				stat[group][2] += y;

				//update xmax
				if(x > stat[group][xstart+0])
					stat[group][xstart+0] = x;
				//update ymin
				if(x < stat[group][xstart+1])
					stat[group][xstart+1] = x;
				//x total
				stat[group][xstart+2] += x;

				//update size
				stat[group][tsize-1]++;
			}
		}			
	}


		
	int outx = 0, outy = 0;
	boolean found = false;
	int maxfound = 0;
	for(int i = 0; i < next; i++){
		if(stat[i][tsize-1] > 103 & stat[i][tsize-1] > maxfound){
			outy = stat[i][2] /stat[i][tsize-1];
			outx = stat[i][xstart + 2] /stat[i][tsize-1];
			found = true;
			maxfound = stat[i][tsize-1];
			
		}
	}

	location = 0;
	for ( int y = 0; y < img.getHeight(); y++){
		for ( int x = 0; x < img.getWidth(); x++){
			location ++;
			if(outx == x & outy == y){
				data[location+1] = 0xFFFF0000;
				data[location] = 0xFFFF0000;
				data[location-1] = 0xFFFF0000;
			}
		}
	}
	
	System.out.println("frame " + frame + " " + outx + " " + outy );
	frame ++;
			
	if(found){
       	 	return new int[]{outx,outy,outx+1,outy+1};
	}
	else{
		return new int[]{0,0,1,1};
	}
    }
 int frame = 0;
    public void run()
    {
        is.start();
        ImageSourceFormat fmt = is.getCurrentFormat();

        // Initialize visualization environment now that we know the image dimensions

        while(true) {
            // read a frame
            byte buf[] = is.getFrame().data;
            if (buf == null)
                continue;

            // Grab the image, and convert it to gray scale immediately
            BufferedImage im = ImageConvert.convertToImage(fmt.format, fmt.width, fmt.height, buf);

            int bounds[] = findLED(im, pg);

            // Display the detection, by drawing on the image
            if (true) {
                // draw the horizontal lines
                for (int y : new int[]{bounds[1], bounds[3]})
                    for (int x = bounds[0]; x <=bounds[2]; x++) {
                        im.setRGB(x,y, 0xffff0000); //Go Blue!
                    }

                // draw the horizontal lines
                for (int x : new int[]{bounds[0], bounds[2]})
                    for (int y = bounds[1]; y <=bounds[3]; y++) {
                        im.setRGB(x,y, 0xffff0000); //Go Blue!
                    }

                jim.setImage(im);
            }
        }
    }

    public static void main(String args[]) throws IOException
    {
        ArrayList<String> urls = ImageSource.getCameraURLs();

        String url = null;
        if (urls.size()==1)
            url = urls.get(0);

        if (args.length > 0)
            url = args[0];

        if (url == null) {
            System.out.printf("Cameras found:\n");
            for (String u : urls)
                System.out.printf("  %s\n", u);
            System.out.printf("Please specify one on the command line.\n");
            return;
        }

        ImageSource is = ImageSource.make(url);
        new SwingLedTracker(is).run();
    }
}
