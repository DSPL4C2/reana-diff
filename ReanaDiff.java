import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.nio.file.Paths;

public class ReanaDiff {
  private static final String OUT_DIR = "./diff_";

  public static Optional<String> getExtensionByStringHandling(String filename) {
    return Optional.ofNullable(filename)
      .filter(f -> f.contains("."))
      .map(f -> f.substring(filename.lastIndexOf(".") + 1));
  }

  public static ArrayList<String> get_features(String filename) throws IOException {
    String regex = "([^()&|!]+)";
    Pattern p = Pattern.compile(regex);

    FileInputStream f1 = new FileInputStream(filename);
    BufferedReader br = new BufferedReader(new InputStreamReader(f1));

    String exp1;
    exp1 = br.readLine().replaceAll("\\s+", "");
    Matcher m = p.matcher(exp1);

    ArrayList<String> features = new ArrayList<String>();

    while (m.find()) {
      String fe = m.group();
      if (!features.contains(fe) && !fe.equals("True") && !fe.equals("False")) {
        features.add(m.group());
      }
    }

    f1.close(); 

    return features;
  }

  public static void main(String[] args) throws IOException {

      // Instantiate the Factory
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

      if (args.length != 2) {
        System.out.print("Usage: java ReanaDiff.java <initial behavioral model path> <final behavioral model path>");
        return;
      }

      String filename_1 = args[0];
      String filename_2 = args[1];

      if (getExtensionByStringHandling(filename_1).equals(Optional.of("xml"))) {
        try {
            // optional, but recommended
            // process XML securely, avoid attacks like XML External Entities (XXE)
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            // parse XML file
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc_1 = db.parse(new File(filename_1));
            Document doc_2 = db.parse(new File(filename_2));

            // optional, but recommended
            // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc_1.getDocumentElement().normalize();
            doc_2.getDocumentElement().normalize();

            // get all <SequenceDiagram>
            NodeList list_1 = doc_1.getElementsByTagName("SequenceDiagram");
            NodeList list_2 = doc_2.getElementsByTagName("SequenceDiagram");

            Map<String, ArrayList<String>> messages1_map = new HashMap<String, ArrayList<String>>();
            Map<String, ArrayList<String>> messages2_map = new HashMap<String, ArrayList<String>>();

            Map<String, ArrayList<String>> diff_guard = new HashMap<String, ArrayList<String>>();

            for (int temp = 0; temp < list_1.getLength(); temp++) {
                Node node = list_1.item(temp);

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;

                    // get sequence diagrams's name
                    String id = element.getAttribute("name");
                    String guard = element.getAttribute("guard");

                    diff_guard.put(id, new ArrayList<String>());
                    diff_guard.get(id).add(guard);

                    messages1_map.put(id, new ArrayList<String>());

                    // get messages
                    NodeList messages = element.getElementsByTagName("Message");
                    
                    for (int i = 0; i < messages.getLength(); i++) {
                        ArrayList<String> m = messages1_map.get(id);
                        m.add(((Element) messages.item(i)).getAttribute("name"));
                    }

                }
            }

            for (int temp = 0; temp < list_2.getLength(); temp++) {
              Node node = list_2.item(temp);

              if (node.getNodeType() == Node.ELEMENT_NODE) {
                  Element element = (Element) node;

                    // get sequence diagrams's name 
                    String id = element.getAttribute("name");
                    String guard = element.getAttribute("guard");

                    messages2_map.put(id, new ArrayList<String>());

                    if (diff_guard.containsKey(id)) {
                      diff_guard.get(id).add(guard);
                    }

                    // get messages
                    NodeList messages = element.getElementsByTagName("Message");
                    
                    for (int i = 0; i < messages.getLength(); i++) {
                        ArrayList<String> m = messages2_map.get(id);
                        m.add(((Element) messages.item(i)).getAttribute("name"));
                    }

                }
          }

        ArrayList<String> diff_fragments = new ArrayList<String>();
        Map<String, ArrayList<String>> diff_messages =  new HashMap<String, ArrayList<String>>();

        for (Map.Entry<String, ArrayList<String>> entry: messages2_map.entrySet()) {
          String key = entry.getKey();
          diff_fragments.add(key);
        }


        for (Map.Entry<String, ArrayList<String>> entry: messages1_map.entrySet()) {
          String key = entry.getKey();
          diff_fragments.remove(key);
        }


        for (Map.Entry<String, ArrayList<String>> entry: messages2_map.entrySet()) {
          String key = entry.getKey();

          ArrayList<String> val_1 = new ArrayList<String> (entry.getValue());
          ArrayList<String> val_2 = messages1_map.get(key);

          if (val_2 != null) {
            val_1.removeAll(val_2);
          }

          if (val_1.size() > 0) {
            diff_messages.put(key, val_1);
          }
        }

        String f1_name = Paths.get(filename_1).getFileName().toString();
        String f2_name = Paths.get(filename_2).getFileName().toString();

        PrintWriter file_diff_frags = new PrintWriter("frags_" + f1_name + "_" + f2_name + ".txt");
        PrintWriter file_msg_frags = new PrintWriter("msg_" + f1_name + "_" + f2_name + ".txt");
        PrintWriter file_pc_frags = new PrintWriter("pc_" + f1_name + "_" + f2_name + ".txt");

        if (diff_fragments.size() > 0) {
          System.out.println("New Fragments:");

          for (String key: diff_fragments) {
            System.out.println('\t' + key);
            file_diff_frags.println(key);
          }
        }

        if (diff_messages.size() > 0) {
          System.out.println("New Messages:");

          for (Map.Entry<String, ArrayList<String>> entry: diff_messages.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> val = entry.getValue();
            
            if (val.size() > 0) {
              System.out.println("\tInside fragment " + key + ":");
              file_msg_frags.print(key+":");

              for (String s: val) {
                System.out.println("\t\t" + s);
                file_msg_frags.print(s + ";");
              }

              file_msg_frags.println();
            }
          }
        }

        if (diff_guard.size() > 0) {
          System.out.println("Guard condition:");
          
          for (Map.Entry<String, ArrayList<String>> entry: diff_guard.entrySet()) {
            String key = entry.getKey();
            ArrayList<String> val = entry.getValue();

            if (!val.get(0).equals(val.get(1))) {
              System.out.println("\t" + key + ": \"" + val.get(0) + "\" => \"" + val.get(1) + "\"");
              file_pc_frags.println(key + ": \"" + val.get(0) + "\" => \"" + val.get(1) + "\"");
            }
          }
        }

        file_diff_frags.close();
        file_msg_frags.close();
        file_pc_frags.close();

        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

    } else if (getExtensionByStringHandling(filename_1).equals(Optional.of("txt"))) {

      try {
        ArrayList<String> features_1 = get_features(filename_1);
        ArrayList<String> features_2 = get_features(filename_2);

        ArrayList<String> new_features = new ArrayList<String>();

        for (String fe: features_2) {
          if (!features_1.contains(fe)) new_features.add(fe);
        }

        System.out.println("New features:");
        for (String fe: new_features) {
          System.out.println("\t" + fe);
        }

      } catch (FileNotFoundException e) {
      System.out.println("Can not open input file");
      return;
    }
  }
  }
}