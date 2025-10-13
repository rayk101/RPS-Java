package M3;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.io.FileReader;
import java.io.IOException;



/*
Challenge 3: Mad Libs Generator (Randomized Stories)
-----------------------------------------------------
- Load a **random** story from the "stories" folder
- Extract **each line** into a collection (i.e., ArrayList)
- Prompts user for each placeholder (i.e., <adjective>) 
    - Any word the user types is acceptable, no need to verify if it matches the placeholder type
    - Any placeholder with underscores should display with spaces instead
- Replace placeholders with user input (assign back to original slot in collection)
- Date: 10/13/25
- Steps to solve: 1. Randomly select a story text file from the story folder usign File[] and Random
2. Reach each line of the selected line into an array using scanner
3. detect the adjectives placeholders and prompt user for input using Scanner.nextLine() 
4. Replace placeholders in each line with the users input and update the original list 
5. print the final story by joining all lines
*/

public class MadLibsGenerator extends BaseClass {
    private static final String STORIES_FOLDER = "M3/stories";
    private static String ucid = "rk975"; // <-- change to your ucid

    public static void main(String[] args) {
        printHeader(ucid, 3,
                "Objective: Implement a Mad Libs generator that replaces placeholders dynamically.");

        Scanner scanner = new Scanner(System.in);
        File folder = new File(STORIES_FOLDER);

        if (!folder.exists() || !folder.isDirectory() || folder.listFiles().length == 0) {
            System.out.println("Error: No stories found in the 'stories' folder.");
            printFooter(ucid, 3);
            scanner.close();
            return;
        }
        List<String> lines = new ArrayList<>();
        // Start edits
        File[] storyFiles = folder.listFiles();
        File selectedFile = storyFiles[new Random().nextInt(storyFiles.length)];
        try {
            Scanner fileScanner = new Scanner(selectedFile);

            while (fileScanner.hasNextLine()) { 
                lines.add(fileScanner.nextLine());
            }

            fileScanner.close();

            for (int i = 0; i < lines.size(); i++){
                String line = lines.get(i);
                while (line.contains("<") && line.contains(">")){
                    int start = line.indexOf("<");
                    int end = line.indexOf(">", start);
                    if (start >= 0 && end > start){
                        String placeholder = line.substring(start + 1, end);
                        String displayPrompt = placeholder.replace("_", " ");
                        System.out.print("Enter a(n) " + displayPrompt + ": ");
                        String userInput = scanner.nextLine(); 
                        line = line.substring(0, start) + userInput + line.substring(end + 1);
                    } else {
                        break;
                    }

                }
                lines.set(i, line);
            }

        } catch (FileNotFoundException e){
            System.out.println("Error,could not read the stories file");
            printFooter(ucid, 3);
            scanner.close();
            return;
        }

        
        System.out.println("\nYour Completed Mad Libs Story:\n");
        StringBuilder finalStory = new StringBuilder();
        for (String line : lines) {
            finalStory.append(line).append("\n");
        }
        System.out.println(finalStory.toString());

        printFooter(ucid, 3);
        scanner.close();
    }
}