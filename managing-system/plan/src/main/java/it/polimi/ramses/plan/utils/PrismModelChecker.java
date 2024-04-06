package it.polimi.ramses.plan.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

@Slf4j
public class PrismModelChecker {
    public static Double convertJsonToPrism(String currentGraph, String destinationGraph) {
        Instant generatePrismStart = Instant.now();
        Gson gson = new Gson();
        JsonObject currentJson = gson.fromJson(currentGraph, JsonObject.class);
        JsonObject destinationJson = gson.fromJson(destinationGraph, JsonObject.class);

        double responseTimeMaximum = 1000.0;
        double vulnerabilityMaximum = 100.0;

        double weightAvailability = currentJson.getAsJsonObject("weights").getAsJsonPrimitive("Availability").getAsDouble();
        double weightResponseTime = currentJson.getAsJsonObject("weights").getAsJsonPrimitive("AverageResponseTime").getAsDouble();
        double weightVulnerability = currentJson.getAsJsonObject("weights").getAsJsonPrimitive("Vulnerability").getAsDouble();

        double oldRestaurantAvailability = currentJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double oldRestaurantResponseTime = currentJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double oldRestaurantVulnerability = currentJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double oldDeliveryAvailability = currentJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double oldDeliveryResponseTime = currentJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double oldDeliveryVulnerability = currentJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double oldPaymentAvailability = currentJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double oldPaymentResponseTime = currentJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double oldPaymentVulnerability = currentJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double oldOrderingAvailability = currentJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double oldOrderingResponseTime = currentJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double oldOrderingVulnerability = currentJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double restaurantAvailablity = destinationJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double restaurantResponseTime = destinationJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double restaurantVulnerability = destinationJson.getAsJsonObject("RESTAURANT-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double deliveryAvailability = destinationJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double deliveryResponseTime = destinationJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double deliveryVulnerability = destinationJson.getAsJsonObject("DELIVERY-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double paymentAvailability = destinationJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double paymentResponseTime = destinationJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double paymentVulnerability = destinationJson.getAsJsonObject("PAYMENT-PROXY-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        double orderingAvailability = destinationJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Availability").getAsDouble();
        double orderingResponseTime = destinationJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("AverageResponseTime").getAsDouble() / responseTimeMaximum;
        double orderingVulnerability = destinationJson.getAsJsonObject("ORDERING-SERVICE").getAsJsonObject("values").getAsJsonPrimitive("Vulnerability").getAsDouble() / vulnerabilityMaximum;

        StringBuilder prismModel = new StringBuilder();
        prismModel.append("dtmc\n\n");

        // availabilities
        prismModel.append("// availabilities\n");
        prismModel.append("const double w_a = ").append(weightAvailability).append(";\n");
        prismModel.append("const double old_a_delivery = ").append(oldDeliveryAvailability).append(";\n");
        prismModel.append("const double old_a_payment = ").append(oldPaymentAvailability).append(";\n");
        prismModel.append("const double old_a_restaurant = ").append(oldRestaurantAvailability).append(";\n");
        prismModel.append("const double old_a_ordering = ").append(oldOrderingAvailability).append(";\n");
        prismModel.append("const double a_delivery = ").append(deliveryAvailability).append(";\n");
        prismModel.append("const double a_payment = ").append(paymentAvailability).append(";\n");
        prismModel.append("const double a_restaurant = ").append(restaurantAvailablity).append(";\n");
        prismModel.append("const double a_ordering = ").append(orderingAvailability).append(";\n");
        prismModel.append("const double denom = (a_payment * a_delivery * a_delivery) - (2 * a_payment * a_delivery) - a_delivery + 3;\n\n");

        // response times
        prismModel.append("// response times\n");
        prismModel.append("const double w_rt = ").append(weightResponseTime).append(";\n");
        prismModel.append("const double old_rt_delivery = ").append(oldDeliveryResponseTime).append(";\n");
        prismModel.append("const double old_rt_payment = ").append(oldPaymentResponseTime).append(";\n");
        prismModel.append("const double old_rt_restaurant = ").append(oldRestaurantResponseTime).append(";\n");
        prismModel.append("const double old_rt_ordering = ").append(oldOrderingResponseTime).append(";\n");
        prismModel.append("const double rt_delivery = ").append(deliveryResponseTime).append(";\n");
        prismModel.append("const double rt_payment = ").append(paymentResponseTime).append(";\n");
        prismModel.append("const double rt_restaurant = ").append(restaurantResponseTime).append(";\n");
        prismModel.append("const double rt_ordering = ").append(orderingResponseTime).append(";\n\n");

        // vulnerabilities
        prismModel.append("// vulnerabilities\n");
        prismModel.append("const double w_v = ").append(weightVulnerability).append(";\n");
        prismModel.append("const double old_v_delivery = ").append(oldDeliveryVulnerability).append(";\n");
        prismModel.append("const double old_v_payment = ").append(oldPaymentVulnerability).append(";\n");
        prismModel.append("const double old_v_restaurant = ").append(oldRestaurantVulnerability).append(";\n");
        prismModel.append("const double old_v_ordering = ").append(oldOrderingVulnerability).append(";\n");
        prismModel.append("const double v_delivery = ").append(deliveryVulnerability).append(";\n");
        prismModel.append("const double v_payment = ").append(paymentVulnerability).append(";\n");
        prismModel.append("const double v_restaurant = ").append(restaurantVulnerability).append(";\n");
        prismModel.append("const double v_ordering = ").append(orderingVulnerability).append(";\n\n");

        // rewards
        prismModel.append("rewards\n");
        prismModel.append("s=1 : (w_a * (1 - a_ordering) + w_rt * rt_ordering + w_v * v_ordering) / (w_a * (1 - old_a_ordering) + w_rt * old_rt_ordering + w_v * old_v_ordering);\n");
        prismModel.append("s=2 : (w_a * (1 - a_delivery) + w_rt * rt_delivery + w_v * v_delivery) / (w_a * (1 - old_a_delivery) + w_rt * old_rt_delivery + w_v * old_v_delivery) * (w_a * (1 - a_payment) + w_rt * rt_payment + w_v * v_payment) / (w_a * (1 - old_a_payment) + w_rt * old_rt_payment + w_v * old_v_payment);\n");
        prismModel.append("s=3 : (w_a * (1 - a_delivery) + w_rt * rt_delivery + w_v * v_delivery) / (w_a * (1 - old_a_delivery) + w_rt * old_rt_delivery + w_v * old_v_delivery);\n");
        prismModel.append("s=4 : (w_a * (1 - a_restaurant) + w_rt * rt_restaurant + w_v * v_restaurant) / (w_a * (1 - old_a_restaurant) + w_rt * old_rt_restaurant + w_v * old_v_restaurant);\n\n");
        prismModel.append("endrewards\n\n");


        // module sefa
        prismModel.append("module sefa\n");
        prismModel.append("\ts : [0..7] init 0;\n\n");

        prismModel.append("\t[] s=0 -> 1 : (s'=1);\n");

        prismModel.append("\t[] s=1 -> (a_ordering / denom) : (s'=2) + (a_ordering * (1 - a_payment * a_delivery) / denom) : (s'=3) + (a_ordering * (1 - a_payment * a_delivery) * (1 - a_delivery) / denom) : (s'=4) + (1 - a_ordering) : (s'=6);\n");
        prismModel.append("\t[] s=2 -> a_payment * a_delivery : (s'=4) + (1 - a_payment * a_delivery) : (s'=1);\n");
        prismModel.append("\t[] s=3 -> a_delivery : (s'=4) + (1 - a_delivery) : (s'=1);\n");
        prismModel.append("\t[] s=4 -> a_restaurant : (s'=5) + (1 - a_restaurant) : (s'=6);\n");

        prismModel.append("\t[] s=5 -> 1 : (s'=5);\n");
        prismModel.append("\t[] s=6 -> 1 : (s'=0);\n");
        prismModel.append("endmodule\n\n");


        // Write the PRISM model to a file
        try {
            Files.write(Paths.get("model.prism"), prismModel.toString().getBytes());
        } catch (IOException e) {
            log.debug("I was serching in this directory: {}", Paths.get("").toAbsolutePath());
            log.error("Error writing PRISM model to file: {}", e.getMessage());
        }

        // Create "model.props" file
        try {
            Files.write(Paths.get("model.props"), "Rmin = ? [ C ]".getBytes());
        } catch (IOException e) {
            log.error("Error writing model.props file: {}", e.getMessage());
        }
        Instant generatePrismEnd = Instant.now();
        log.info("Time to generate and save PRISM model: {}ms", Duration.between(generatePrismStart, generatePrismEnd).toNanos() / 1000000.0);


        Instant obtainCostStart = Instant.now();
        // Execute the PRISM command: prism model.prism model.props -prop 2
        try {
            Process process = Runtime.getRuntime().exec("./prism/bin/prism model.prism model.props");
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                InputStream inputStream = process.getInputStream();
                String result = parsePrismOutput(inputStream);

                log.info("Reward Value: " + result);
                Instant obtainCostEnd = Instant.now();
                log.info("Time to obtain the cost: {}ms", Duration.between(obtainCostStart, obtainCostEnd).toMillis());

                return result == null ? null : Double.parseDouble(result);
            } else {
                log.error("Error executing PRISM command. Exit code: {}", exitCode);
                InputStream inputStream = process.getInputStream();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.error(line);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error executing PRISM command: {}", e.getMessage());
        }

        return null;
    }

    private static String parsePrismOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Value in the initial state")) {
                    String[] tokens = line.split(":");
                    if (tokens.length > 1) {
                        return tokens[1].trim();
                    }
                }
            }
        }
        return null;
    }
}
