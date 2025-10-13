package M3;
import java.text.DecimalFormat;

/*
Challenge 1: Command-Line Calculator
------------------------------------
- Accept two numbers and an operator as command-line arguments
- Supports addition (+) and subtraction (-)
- Allow integer and floating-point numbers
- Ensures correct decimal places in output based on input (e.g., 0.1 + 0.2 → 1 decimal place)
- Display an error for invalid inputs or unsupported operators
- Capture 5 variations of tests
- Date: 10/13/25
- Steps to solve: 1. Parse through the user inputs
  2. Validate the type of operator 
  3. Detect if there is decimal usage 
  4. perform the calculation of either addition or subtraction. 
  5. format the output correclty. 
*/

public class CommandLineCalculator extends BaseClass {
    private static String ucid = "rk975"; // <-- change to your ucid

    public static void main(String[] args) {
        printHeader(ucid, 1, "Objective: Implement a calculator using command-line arguments.");

        if (args.length != 3) {
            System.out.println("Usage: java M3.CommandLineCalculator <num1> <operator> <num2>");
            printFooter(ucid, 1);
            return;
        }

        try {
            System.out.println("Calculating result...");
            // extract the equation (format is <num1> <operator> <num2>)
            String num1Str = args[0];
            String num2Str = args[2];
            String operator = args[1]; 
            // check if operator is addition or subtraction
        if (!operator.equals("+") && !operator.equals("-")) {
            System.out.println("Error: Unsupported operator. Use + or -.");
            printFooter(ucid, 1);
            return;
}


            // check the type of each number and choose appropriate parsing
            double num1 = Double.parseDouble(num1Str);
            double num2 = Double.parseDouble(num2Str);
            double result = operator.equals("+") ? num1 + num2 : num1 - num2; 

            int precision1 = getDecimalPlaces(num1Str);
            int precision2 = getDecimalPlaces(num2Str);
            int maxPrecision = Math.max(precision1, precision2);
            // generate the equation result (Important: ensure decimals display as the
            // longest decimal passed)
            StringBuilder pattern = new StringBuilder("0");
            if (maxPrecision > 0 ){
                pattern.append(".");
                for (int i = 0; i < maxPrecision; i++){
                    pattern.append("0");
                }
            }
            DecimalFormat df = new DecimalFormat(pattern.toString());
            System.out.println("Result: " + df.format(result));
            // i.e., 0.1 + 0.2 would show as one decimal place (0.3), 0.11 + 0.2 would shows
            // as two (0.31), etc

        } catch (Exception e) {
            System.out.println("Invalid input. Please ensure correct format and valid numbers.");
        }

        printFooter(ucid, 1);
    }
    private static int getDecimalPlaces(String numStr){
        int index = numStr.indexOf(".");
        return index < 0 ? 0 : numStr.length() - index - 1; 
    }
}