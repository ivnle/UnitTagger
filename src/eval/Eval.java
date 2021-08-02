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
        final File outputFile = new File("data/output.jsonl");
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
                // replace span of hdr with qqqq
                String hdr_masked = hdr.substring(0, num_span.get(0)) + "qqqq" + hdr.substring(num_span.get(1));

                List<? extends EntryWithScore<Unit>> extractedUnits = parser.parseHeaderExplain(hdr_masked, applicableRules, 0, context);
        
                if (extractedUnits == null) continue ;
                for (EntryWithScore<Unit> _unit : extractedUnits) {
                    UnitSpan unitSpan = (UnitSpan) _unit;
                    Integer start = unitSpan.start();
                    Integer end = unitSpan.end();
                    String unit = unitSpan.getKey().getName();
                    String symbol = unitSpan.getKey().getSymbol();
                    System.out.println(context[0].tokens.subList(start, end+1));
                    System.out.println(start + " " + end + " " + unit + " " + symbol);

                    numUnitPred.num = numUnit.num;
                    numUnitPred.num_span = numUnit.num_span;
                    numUnitPred.unit = unitSpan.getKey().getName();
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
