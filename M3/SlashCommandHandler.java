package M3;

/*
Challenge 2: Simple Slash Command Handler
-----------------------------------------
- Accept user input as slash commands
  - "/greet <name>" → Prints "Hello, <name>!"
  - "/roll <num>d<sides>" → Roll <num> dice with <sides> and returns a single outcome as "Rolled <num>d<sides> and got <result>!"
  - "/echo <message>" → Prints the message back
  - "/quit" → Exits the program
- Commands are case-insensitive
- Print an error for unrecognized commands
- Print errors for invalid command formats (when applicable)
- Capture 3 variations of each command except "/quit"
- Date: 10/13/25
- Steps to Solve: 1. Capture the input from the console using scanner.line  
2. conver the the input to lowercase using toLowerCase() and then get rid of whitespace 
3. find out which slash command was used and extract the arguments,using startswith() or equalIgnoreCase() 
4. perform the action - correct response. Using a while loop to go over responses 
5. handle any errors usign a while a clause. 
*/

import java.util.Scanner;
import java.util.Random;

public class SlashCommandHandler extends BaseClass {
    private static String ucid = "rk975"; // <-- change to your UCID

    public static void main(String[] args) {
        printHeader(ucid, 2, "Objective: Implement a simple slash command parser.");

        Scanner scanner = new Scanner(System.in);
        Random rand = new Random(); 
        // Can define any variables needed here

        while (true) {
            System.out.print("Enter command: ");
            String input  = scanner.nextLine().trim();

            if(input.equalIgnoreCase("/quit")){
                System.out.println("Exiting the program");
                break; 
            } else if (input.toLowerCase().startswith("/greet")){
                String[] parts = input.split(" ", 2);
                if (parts.length < 2 || parts[1].isempty()){
                    System.out.println("Error, missing name for /greet.");
                } else {
                    System.out.println("Hello, " + parts[1] + "!");
                }
            }
            // get entered text

            // check if greet
            //// process greet

            // check if roll
            //// process roll
            //// handle invalid formats

            // check if echo
            //// process echo

            // check if quit
            //// process quit

            // handle invalid commnads

            // delete this condition/block, it's just here so the sample runs without edits
            if (1 == 1) {
                System.out.println("Breaking loop");
                break;
            }
        }

        printFooter(ucid, 2);
        scanner.close();
    }
}