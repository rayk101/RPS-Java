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

            if(input.equalsIgnoreCase("/quit")){
                System.out.println("Exiting the program");
                break; 
            } else if (input.toLowerCase().startsWith("/greet")){
                String[] parts = input.split(" ", 2);
                if (parts.length < 2 || parts[1].isEmpty()){
                    System.out.println("Error, missing name for /greet.");
                } else {
                    System.out.println("Hello, " + parts[1] + "!");
                }
            } else if (input.toLowerCase().startsWith("/roll ")){
                String [] parts = input.split(" ", 2);
                if (parts.length < 2 || !parts[1].matches("\\d+d\\d+")){
                    System.out.println("Error, invalid format. Use /roll <num>d<sides>.");   
                } else {
                    String[] diceParts = parts[1].toLowerCase().split("d");
                    int num = Integer.parseInt(diceParts[0]);
                    int sides = Integer.parseInt(diceParts[1]);
                    int total = 0; 
                    for (int i = 0; i < num; i++) {
                        total += rand.nextInt(sides) + 1; 
                    }
                    System.out.println("Rolled " + num + "d" + sides + " and got " + total + "!");
                }
                 } else if (input.toLowerCase().startsWith("/echo ")) {    // Step 3: Match /echo
                    String[] parts = input.split(" ", 2);
                    if (parts.length < 2 || parts[1].isEmpty()) {
                        System.out.println("Error: Missing message for /echo."); // Step 5
                }   else {
                        System.out.println(parts[1]);
                }
            }     else {
                    System.out.println("Error: Unrecognized command.");    // Step 5: Unknown command
             }
        }

        printFooter(ucid, 2);
        scanner.close();
    }
}