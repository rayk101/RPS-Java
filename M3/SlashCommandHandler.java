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

public class SlashCommandHandler extends BaseClass {
    private static String ucid = "rk975"; // <-- change to your UCID

    public static void main(String[] args) {
        printHeader(ucid, 2, "Objective: Implement a simple slash command parser.");

        Scanner scanner = new Scanner(System.in);

        // Can define any variables needed here

        while (true) {
            System.out.print("Enter command: ");
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