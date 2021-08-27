package eval;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import catalog.QuantityCatalog;
import catalog.Unit;
import iitb.shared.EntryWithScore;
import iitb.shared.XMLConfigs;
import java_cup.symbol;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import parser.CFGParser4Text;
import parser.HeaderUnitParser;
import parser.ParseState;
import parser.UnitSpan;

public class Eval {

    // This is how you query the units catalog
    // dict.getTopK(parts[p], "", QuantityCatalog.MinMatchThreshold);

    public static void main(String[] args) throws Exception {

        // Read jsonl file from command line into list of examples
        File inputFile = new File(args[0]);  
        File outputFile = new File(args[1]);        
        MappingIterator<Example> iterator = new ObjectMapper().readerFor(Example.class).readValues(inputFile);
        List<Example> examples = iterator.readAll();

        // Initialize parser
        QuantityCatalog dict = new QuantityCatalog((Element)null);
		Element emptyElement = XMLConfigs.emptyElement();
		
        String coOccurMethods[]={"ConceptClassifier"};
        String coOccurMethod = coOccurMethods[0];
		String params[]={""};
        String param = params[0];        
        emptyElement.setAttribute("co-occur-class", coOccurMethod);
        if (param.length() > 0) emptyElement.setAttribute("params",param);
        HeaderUnitParser parser = new CFGParser4Text(emptyElement, dict);
        Vector<String> applicableRules = new Vector<String>();

        // Init jsonl writer
        //final File outputFile = new File("data/output.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        SequenceWriter seq = mapper.writer()
        .withRootValueSeparator("\n") // Important! Default value separator is single space
        .writeValues(outputFile);        

        for (Example example : examples) {
            String hdr = example.text;
            String contextStr = example.context;
            ParseState context[] = new ParseState[1];
			context[0] = new ParseState(contextStr);
            List<NumUnit> numUnits = example.num_units;
            List<NumUnit> numUnitsPred = new ArrayList<NumUnit>();
            for (NumUnit numUnit : numUnits) {
                NumUnit numUnitPred = new NumUnit();
                List<Integer> num_span = numUnit.num_span;
                // if num_span is empty, continue
                if (num_span.size() == 0) continue;
                
                // replace span of hdr with qqqq
                String hdr_masked = hdr.substring(0, num_span.get(0)) + "qqqq" + hdr.substring(num_span.get(1));

                List<? extends EntryWithScore<Unit>> extractedUnits = parser.parseHeaderExplain(hdr_masked, applicableRules, 0, context);
                
                List<List<Integer>> tokenPos = QuantityCatalog.getTokensPos(hdr_masked, null, null, null);
                //System.out.println(hdr_masked);
                //System.out.println(context[0].tokens);
                //System.out.println(tokenPos);


                if (extractedUnits == null) continue ;
                for (EntryWithScore<Unit> extractedUnit : extractedUnits) {
                    UnitSpan extrUnitSpan = (UnitSpan) extractedUnit;
                    Integer start = extrUnitSpan.start();
                    Integer end = extrUnitSpan.end();
                    String unit = extrUnitSpan.getKey().getName();
                    String symbol = extrUnitSpan.getKey().getSymbol();
                    
                    // Keep track of how quant mask changes original string positions
                    Integer offset = 0;
                    if (num_span.get(0) < tokenPos.get(start).get(0)) {
                        offset = num_span.get(1) - num_span.get(0) - 4;
                    }

                    //TODO map sublist token thing to span
                    //System.out.println(context[0].tokens.subList(start, end+1));
                    //System.out.println(start + " " + end + " " + unit + " " + symbol);
                    
                    //System.out.println(tokenPos.subList(start, end+1));                    
                    
                    String o = hdr_masked.substring(tokenPos.get(start).get(0), tokenPos.get(end).get(1));
                    //System.out.println(o);
                    
                    List<Integer> unit_span = new ArrayList<Integer>();
                    unit_span.add(tokenPos.get(start).get(0) + offset);
                    unit_span.add(tokenPos.get(end).get(1) + offset);
                    
                    //numUnitPred.unit_span = symbol;
                    numUnitPred.num = numUnit.num;
                    numUnitPred.num_span = numUnit.num_span;
                    numUnitPred.unit = extrUnitSpan.getKey().getName();
                    numUnitPred.unit_span = unit_span;
                    break;
                }
                numUnitsPred.add(numUnitPred);
            }            
            Example prediction = new Example();
            prediction.id = example.id;
            prediction.text = example.text;
            prediction.num_units = numUnitsPred;
            seq.write(prediction);

        }        


    }
}
