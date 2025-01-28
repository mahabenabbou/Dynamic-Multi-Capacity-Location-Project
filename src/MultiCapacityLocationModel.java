import ilog.concert.*;
import ilog.cplex.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MultiCapacityLocationModel {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Multi-Capacity Location Model");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(0, 2, 10, 10));

            // Input Fields
            JLabel labelN = new JLabel("Number of clients (N):");
            JTextField fieldN = new JTextField();
            panel.add(labelN);
            panel.add(fieldN);

            JLabel labelM = new JLabel("Number of potential sites (M):");
            JTextField fieldM = new JTextField();
            panel.add(labelM);
            panel.add(fieldM);

            JLabel labelL = new JLabel("Number of capacity levels (L):");
            JTextField fieldL = new JTextField();
            panel.add(labelL);
            panel.add(fieldL);

            JLabel labelT = new JLabel("Number of periods (T):");
            JTextField fieldT = new JTextField();
            panel.add(labelT);
            panel.add(fieldT);

            JButton submitButton = new JButton("Submit");
            panel.add(submitButton);

            JTextArea resultArea = new JTextArea(10, 50);
            resultArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(resultArea);

            frame.getContentPane().add(panel, BorderLayout.NORTH);
            frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

            submitButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        // Parse Inputs
                        int N = Integer.parseInt(fieldN.getText());
                        int M = Integer.parseInt(fieldM.getText());
                        int L = Integer.parseInt(fieldL.getText());
                        int T = Integer.parseInt(fieldT.getText());

                        // Prompt user for dynamic inputs
                        int[][] d = new int[N][T];
                        int[][][] c = new int[N][M][T];
                        int[] u = new int[L];
                        int[][] f = new int[L][T];
                        int[] B = new int[T];
                        int[] H = new int[T];

                        // Collect demands
                        for (int i = 0; i < N; i++) {
                            for (int t = 0; t < T; t++) {
                                String input = JOptionPane.showInputDialog(
                                        frame, String.format("Demand for Client %d, Period %d:", i + 1, t + 1));
                                d[i][t] = Integer.parseInt(input);
                            }
                        }

                        // Collect transportation costs
                        for (int i = 0; i < N; i++) {
                            for (int j = 0; j < M; j++) {
                                for (int t = 0; t < T; t++) {
                                    String input = JOptionPane.showInputDialog(
                                            frame, String.format("Transportation cost for Client %d, Site %d, Period %d:", i + 1, j + 1, t + 1));
                                    c[i][j][t] = Integer.parseInt(input);
                                }
                            }
                        }

                        // Collect facility capacities
                        for (int k = 0; k < L; k++) {
                            String input = JOptionPane.showInputDialog(
                                    frame, String.format("Capacity for Level %d:", k + 1));
                            u[k] = Integer.parseInt(input);
                        }

                        // Collect facility opening costs
                        for (int k = 0; k < L; k++) {
                            for (int t = 0; t < T; t++) {
                                String input = JOptionPane.showInputDialog(
                                        frame, String.format("Opening cost for Level %d, Period %d:", k + 1, t + 1));
                                f[k][t] = Integer.parseInt(input);
                            }
                        }

                        // Collect budgets
                        for (int t = 0; t < T; t++) {
                            String input = JOptionPane.showInputDialog(
                                    frame, String.format("Budget for Period %d:", t + 1));
                            B[t] = Integer.parseInt(input);
                        }

                        // Collect operating costs
                        for (int t = 0; t < T; t++) {
                            String input = JOptionPane.showInputDialog(
                                    frame, String.format("Operating cost for Period %d:", t + 1));
                            H[t] = Integer.parseInt(input);
                        }

                        // Solve the model using CPLEX
                        String solution = solveModel(N, M, L, T, d, c, u, f, B, H);
                        resultArea.setText(solution);

                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            frame.setVisible(true);
        });
    }

    private static String solveModel(int N, int M, int L, int T, int[][] d, int[][][] c, int[] u, int[][] f, int[] B, int[] H) {
        StringBuilder result = new StringBuilder();
        try {
            IloCplex cplex = new IloCplex();

            // Decision Variables
            IloNumVar[][][] q = new IloNumVar[N][M][T];
            IloNumVar[][][] y = new IloNumVar[M][L][T];

            // Transportation Quantity Variables
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    for (int t = 0; t < T; t++) {
                        q[i][j][t] = cplex.numVar(0, Double.MAX_VALUE, "q_" + (i + 1) + "" + (j + 1) + "" + (t + 1));
                    }
                }
            }

            // Facility Opening Variables
            for (int j = 0; j < M; j++) {
                for (int k = 0; k < L; k++) {
                    for (int t = 0; t < T; t++) {
                        y[j][k][t] = cplex.intVar(0, 1, "y_" + (j + 1) + "" + (k + 1) + "" + (t + 1));
                    }
                }
            }

            // Objective Function
            IloLinearNumExpr objective = cplex.linearNumExpr();
            for (int t = 0; t < T; t++) {
                // Transportation Cost
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < M; j++) {
                        objective.addTerm(d[i][t] * c[i][j][t], q[i][j][t]);
                    }
                }

                // Facility Opening Cost
                for (int j = 0; j < M; j++) {
                    for (int k = 0; k < L; k++) {
                        objective.addTerm(f[k][t], y[j][k][t]);
                        objective.addTerm(H[t], y[j][k][t]);
                    }
                }
            }
            cplex.addMinimize(objective);

            // Constraints

            // 1. Client Demand Satisfaction
            for (int i = 0; i < N; i++) {
                for (int t = 0; t < T; t++) {
                    IloLinearNumExpr demandExpr = cplex.linearNumExpr();
                    for (int j = 0; j < M; j++) {
                        demandExpr.addTerm(1, q[i][j][t]);
                    }
                    cplex.addEq(demandExpr, d[i][t], "Demand_Satisfaction_" + (i+1) + "_" + (t+1));
                }
            }

            // 2. Facility Capacity
            for (int j = 0; j < M; j++) {
                for (int t = 0; t < T; t++) {
                    IloLinearNumExpr capacityExpr = cplex.linearNumExpr();
                    for (int i = 0; i < N; i++) {
                        capacityExpr.addTerm(1, q[i][j][t]);
                    }

                    IloLinearNumExpr facilityCapExpr = cplex.linearNumExpr();
                    for (int k = 0; k < L; k++) {
                        facilityCapExpr.addTerm(u[k], y[j][k][t]);
                    }
                    cplex.addLe(capacityExpr, facilityCapExpr, "Facility_Capacity_" + (j+1) + "_" + (t+1));
                }
            }

            // 3. Facility Activation
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    for (int t = 0; t < T; t++) {
                        IloLinearNumExpr activationExpr = cplex.linearNumExpr();
                        for (int k = 0; k < L; k++) {
                            activationExpr.addTerm(1, y[j][k][t]);
                        }

                        // Correction ici
                        cplex.addLe(q[i][j][t],
                                cplex.prod(d[i][t], activationExpr),
                                "Facility_Activation_" + (i+1) + "" + (j+1) + "" + (t+1));
                    }
                }
            }

            // 4. Budget Constraint
            for (int t = 0; t < T; t++) {
                IloLinearNumExpr budgetExpr = cplex.linearNumExpr();
                for (int j = 0; j < M; j++) {
                    for (int k = 0; k < L; k++) {
                        budgetExpr.addTerm(f[k][t] + H[t], y[j][k][t]);
                    }
                }
                cplex.addLe(budgetExpr, B[t], "Budget_" + (t+1));
            }

            // 5. Single Facility Opening
            for (int j = 0; j < M; j++) {
                for (int t = 0; t < T; t++) {
                    IloLinearNumExpr singleFacilityExpr = cplex.linearNumExpr();
                    for (int k = 0; k < L; k++) {
                        singleFacilityExpr.addTerm(1, y[j][k][t]);
                    }
                    cplex.addLe(singleFacilityExpr, 1, "Single_Facility_" + (j+1) + "_" + (t+1));
                }
            }


            if (cplex.solve()) {
                result.append("Solution found!\n");
                result.append("Objective value: ").append(cplex.getObjValue()).append("\n");

                // Affectations et quantités transportées
                result.append("\nAffectations et quantités transportées:\n");
                for (int t = 0; t < T; t++) {
                    result.append(String.format("\nPériode %d:\n", t + 1));
                    for (int i = 0; i < N; i++) {
                        for (int j = 0; j < M; j++) {
                            double quantity = cplex.getValue(q[i][j][t]);
                            if (quantity > 0.001) {  // Évitez les valeurs très petites
                                result.append(String.format(
                                        "Client %d reçoit %.2f unités du site %d\n",
                                        i + 1, quantity, j + 1
                                ));
                            }
                        }
                    }
                }

                // Sites ouverts
                result.append("\nSites ouverts:\n");
                for (int t = 0; t < T; t++) {
                    result.append(String.format("\nPériode %d:\n", t + 1));
                    for (int j = 0; j < M; j++) {
                        for (int k = 0; k < L; k++) {
                            double isOpen = cplex.getValue(y[j][k][t]);
                            if (isOpen > 0.5) {  // Vérifiez si le site est ouvert (valeur binaire)
                                result.append(String.format(
                                        "Site %d est ouvert au niveau %d\n", j + 1, k + 1
                                ));
                            }
                        }
                    }
                }
            } else {
                result.append("No solution found.");
            }

        } catch (IloException e) {
            result.append("Error solving model: ").append(e.getMessage());
        }

        return result.toString();
    }
}
