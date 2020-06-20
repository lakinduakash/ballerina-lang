package org.ballerinalang.jvm.values;

import org.ballerinalang.jvm.JSONParser;
import org.ballerinalang.jvm.XMLNodeType;
import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BMapType;
import org.ballerinalang.jvm.types.BPackage;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.BTypes;
import org.ballerinalang.jvm.types.TypeConstants;
import org.ballerinalang.jvm.values.api.BXML;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convert Ballerina XML value into respective JSON value.
 * @since 1.2.5
 */
public class XmlToJsonConverter {

    private static final String XML_NAMESPACE_PREFIX = "xmlns:";

    private static final BType jsonMapType =
            new BMapType(TypeConstants.MAP_TNAME, BTypes.typeJSON, new BPackage(null, null, null));

    /**
     * Converts given xml object to the corresponding JSON value.
     *
     * @param xml                XML object to get the corresponding json
     * @param attributePrefix    Prefix to use in attributes
     * @param preserveNamespaces preserve the namespaces when converting
     * @return JSON representation of the given xml object
     */
    public static Object convertToJSON(XMLValue xml, String attributePrefix, boolean preserveNamespaces) {
        switch (xml.getNodeType()) {
            case TEXT:
                return JSONParser.parse("\"" + ((XMLText) xml).stringValue() + "\"");
            case ELEMENT:
                return convertElement((XMLItem) xml, attributePrefix, preserveNamespaces);
            case SEQUENCE:
                XMLSequence xmlSequence = (XMLSequence) xml;
                if (xmlSequence.isEmpty()) {
                    return newJsonList();
                }
                return convertXMLSequence(xmlSequence, attributePrefix, preserveNamespaces);
            default:
                return newJsonMap();
        }
    }
    /**
     * Converts given xml object to the corresponding json.
     *
     * @param xmlItem XML element to traverse
     * @param attributePrefix Prefix to use in attributes
     * @param preserveNamespaces preserve the namespaces when converting
     * @return ObjectNode Json object node corresponding to the given xml element
     */
    @SuppressWarnings("unchecked")
    private static Object convertElement(XMLItem xmlItem, String attributePrefix,
                                                               boolean preserveNamespaces) {
        MapValueImpl<String, Object> rootNode = newJsonMap();
        LinkedHashMap<String, String> attributeMap = collectAttributesAndNamespaces(xmlItem, preserveNamespaces);
        String keyValue = getElementKey(xmlItem, preserveNamespaces);
        Object children = convertXMLSequence(xmlItem.getChildrenSeq(), attributePrefix, preserveNamespaces);

        if (attributeMap.isEmpty()) {
            if (children == null) {
                return keyValue;
            }
            rootNode.put(keyValue, children);
            return rootNode;
        } else {
           if (children == null) {
               addAttributes(rootNode, attributePrefix, attributeMap);
               return rootNode;
           } else {
               if (children instanceof ArrayValueImpl) {
                   rootNode.put(keyValue, children);
                   addAttributes(rootNode, attributePrefix, attributeMap);
                   return rootNode;
               } else {
                   addAttributes((MapValueImpl<String, Object>) children, attributePrefix, attributeMap);
                   return children;
               }
           }
        }
    }

    private static void addAttributes(MapValueImpl<String, Object> rootNode, String attributePrefix,
                                      LinkedHashMap<String, String> attributeMap) {
        for (Map.Entry<String, String> entry : attributeMap.entrySet()) {
            rootNode.put(attributePrefix + entry.getKey(), entry.getValue());
        }
    }

    /**
     * Converts given xml sequence to the corresponding json.
     *
     * @param xmlSequence XML sequence to traverse
     * @param attributePrefix Prefix to use in attributes
     * @param preserveNamespaces preserve the namespaces when converting
     * @return JsonNode Json node corresponding to the given xml sequence
     */
    private static Object convertXMLSequence(XMLSequence xmlSequence, String attributePrefix,
                                             boolean preserveNamespaces) {
        List<BXML> sequence = xmlSequence.getChildrenList();
        if (sequence.isEmpty()) {
            return null;
        }

        SequenceConvertibility seqConvertibility = isElementSequenceConvertibleToList(sequence);
        if (seqConvertibility == SequenceConvertibility.SAME_KEY) {
            String elementName = sequence.get(0).getElementName();
            MapValueImpl<String, Object> listWrapper = newJsonMap();
            ArrayValueImpl arrayValue = convertChildrenToJsonList(sequence, attributePrefix, preserveNamespaces);
            listWrapper.put(elementName, arrayValue);
            return listWrapper;
        } else if (seqConvertibility == SequenceConvertibility.ELEMENT_ONLY) {
            MapValueImpl<String, Object> elementObj = newJsonMap();
            for (BXML bxml : sequence) {
                // Skip comments and PI items.
                if (bxml.getNodeType() == XMLNodeType.COMMENT || bxml.getNodeType() == XMLNodeType.PI) {
                    continue;
                }
                String elementName = bxml.getElementName();
                Object elemAsJson = convertElement((XMLItem) bxml, attributePrefix, preserveNamespaces);
                if (elemAsJson instanceof MapValueImpl) {
                    @SuppressWarnings("unchecked")
                    MapValueImpl<String, Object> mapVal = (MapValueImpl<String, Object>) elemAsJson;
                    if (mapVal.size() == 1) {
                        Object val = mapVal.get(elementName);
                        if (val != null) {
                            elementObj.put(elementName, val);
                            continue;
                        }
                    }
                }
                elementObj.put(elementName, elemAsJson);
            }
            return elementObj;
        } else {
            if (sequence.size() == 1) {
                return convertToJSON((XMLValue) sequence.get(0), attributePrefix, preserveNamespaces);
            }
            ArrayList<Object> list = new ArrayList<>();
            for (BXML bxml : sequence) {
                list.add(convertToJSON((XMLValue) bxml, attributePrefix, preserveNamespaces));
            }
            return newJsonListFrom(list);
        }
    }

    private static ArrayValueImpl convertChildrenToJsonList(List<BXML> sequence, String prefix,
                                                            boolean preserveNamespaces) {
        List<Object> list = new ArrayList<>();
        for (BXML child : sequence) {
            list.add(convertToJSON((XMLValue) child.children(), prefix, preserveNamespaces));
        }
        return newJsonListFrom(list);
    }

    private static MapValueImpl<String, Object> newJsonMap() {
        return new MapValueImpl<>(jsonMapType);
    }

    private static SequenceConvertibility isElementSequenceConvertibleToList(List<BXML> sequence) {
        Iterator<BXML> iterator = sequence.iterator();
        BXML next = iterator.next();
        if (next.getNodeType() == XMLNodeType.TEXT) {
            return SequenceConvertibility.LIST;
        }
        String firstElementName = next.getElementName();
        int i = 0;
        boolean sameElementName = true;
        for(; iterator.hasNext(); i++) {
            BXML val = iterator.next();
            if (val.getNodeType() == XMLNodeType.ELEMENT) {
                if (!((XMLItem) val).getElementName().equals(firstElementName)) {
                    sameElementName = false;
                }
            } else if (val.getNodeType() == XMLNodeType.TEXT) {
                return SequenceConvertibility.LIST;
            } else {
                i--; // we don't want `i` to count up for comments and PI items
            }
        }
        return (sameElementName && i > 0) ? SequenceConvertibility.SAME_KEY : SequenceConvertibility.ELEMENT_ONLY;
    }

    private static ArrayValueImpl newJsonList() {
        return new ArrayValueImpl(new BArrayType(BTypes.typeJSON));
    }

    public static ArrayValueImpl newJsonListFrom(List<Object> items) {
        return new ArrayValueImpl(items.toArray(), new BArrayType(BTypes.typeJSON));
    }

    /**
     * Extract attributes and namespaces from the XML element.
     *
     * @param element XML element to extract attributes and namespaces
     * @param preserveNamespaces should namespace attribute be preserved
     */
    private static LinkedHashMap<String, String> collectAttributesAndNamespaces(XMLItem element,
                                                                                boolean preserveNamespaces) {
        int nsPrefixBeginIndex = XMLItem.XMLNS_URL_PREFIX.length() - 1;
        LinkedHashMap<String, String> attributeMap = new LinkedHashMap<>();
        Map<String, String> nsPrefixMap = new HashMap<>();
        for (Map.Entry<String, String> entry : element.getAttributesMap().entrySet()) {
            if (entry.getKey().startsWith(XMLItem.XMLNS_URL_PREFIX)) {
                String prefix = entry.getKey().substring(nsPrefixBeginIndex);
                String ns = entry.getValue();
                nsPrefixMap.put(ns, prefix);
                if (preserveNamespaces) {
                    attributeMap.put(XML_NAMESPACE_PREFIX + prefix, ns);
                }
            }
        }
        for (Map.Entry<String, String> entry : element.getAttributesMap().entrySet()) {
            String key = entry.getKey();
            if (preserveNamespaces && !key.startsWith(XMLItem.XMLNS_URL_PREFIX)) {
                int nsEndIndex = key.lastIndexOf('}');
                String ns = key.substring(1, nsEndIndex);
                String local = key.substring(nsEndIndex);
                String nsPrefix = nsPrefixMap.get(ns);
                if (nsPrefix != null) {
                    attributeMap.put(nsPrefix + ":" + local, entry.getValue());
                } else {
                    attributeMap.put(local, entry.getValue());
                }
            }
        }
        return attributeMap;
    }

    /**
     * Extract the key from the element with namespace information.
     *
     * @param xmlItem XML element for which the key needs to be generated
     * @param preserveNamespaces Whether namespace info included in the key or not
     * @return String Element key with the namespace information
     */
    private static String getElementKey(XMLItem xmlItem, boolean preserveNamespaces) {
        // Construct the element key based on the namespaces
        StringBuilder elementKey = new StringBuilder();
        QName qName = xmlItem.getQName();
        if (preserveNamespaces) {
            String prefix = qName.getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                elementKey.append(prefix).append(':');
            }
        }
        elementKey.append(qName.getLocalPart());
        return elementKey.toString();
    }

    private enum SequenceConvertibility {
        SAME_KEY, ELEMENT_ONLY, LIST
    }
}
