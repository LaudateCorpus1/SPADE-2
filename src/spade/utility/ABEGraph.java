package spade.utility;

import com.mysql.jdbc.StringUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.reporter.audit.OPMConstants;

import javax.crypto.Cipher;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static spade.core.Kernel.FILE_SEPARATOR;
import static spade.transformer.ABE.encryptAnnotation;
import static spade.transformer.ABE.decryptAnnotation;

public class ABEGraph extends Graph
{
	private String lowKey;
	private String mediumKey;
	private String highKey;

	private static final String LOW = "low";
	private static final String MEDIUM = "medium";
	private static final String HIGH = "high";

	public abstract static class AnnotationValue
	{
		protected String annotationValue;
		protected Map<String, AnnotationValue> annotationParts;
		public abstract String getAnnotationValue();
		public abstract void putAnnotationValue(String value);
		public abstract Map<String, AnnotationValue> getAnnotationParts();
		public abstract void putAnnotationPart(String partKey, AnnotationValue value);
		public abstract String encrypt(String key, String plainValue, String level, Cipher cipher);
		public abstract String decrypt(String key, String encryptedValue, String level, Cipher cipher);
	}

	public static class PlainString extends AnnotationValue
	{
		public PlainString(String value)
		{
			annotationValue = value;
		}

		@Override
		public String getAnnotationValue()
		{
			return annotationValue;
		}

		@Override
		public void putAnnotationValue(String value)
		{
			annotationValue = value;
		}

		@Override
		public Map<String, AnnotationValue> getAnnotationParts()
		{
			return null;
		}

		@Override
		public void putAnnotationPart(String partKey, AnnotationValue value)
		{}

		@Override
		public String encrypt(String key, String plainValue, String level, Cipher cipher)
		{
			return plainValue;
		}

		@Override
		public String decrypt(String key, String encryptedValue, String level, Cipher cipher)
		{
			return null;
		}
	}

	public static class EncryptedString extends AnnotationValue
	{
		public EncryptedString(String value)
		{
			annotationValue = value;
		}

		@Override
		public String getAnnotationValue()
		{
			return annotationValue;
		}

		@Override
		public void putAnnotationValue(String value)
		{
			annotationValue = value;
		}

		@Override
		public Map<String, AnnotationValue> getAnnotationParts()
		{
			return null;
		}

		@Override
		public void putAnnotationPart(String partKey, AnnotationValue value)
		{}

		@Override
		public String encrypt(String key, String plainValue, String level, Cipher cipher)
		{
			return null;
		}

		@Override
		public String decrypt(String key, String encryptedValue, String level, Cipher cipher)
		{
			return null;
		}
	}

	public static class EncryptedTime extends AnnotationValue
	{
		public EncryptedTime(String value)
		{
			annotationValue = value;
			annotationParts = new HashMap<>();
		}

		@Override
		public String getAnnotationValue()
		{
			return annotationValue;
		}

		@Override
		public void putAnnotationValue(String value)
		{
			annotationValue = value;
		}

		@Override
		public Map<String, AnnotationValue> getAnnotationParts()
		{
			return annotationParts;
		}

		@Override
		public void putAnnotationPart(String partKey, AnnotationValue value)
		{
			annotationParts.put(partKey, value);
		}

		@Override
		public String encrypt(String key, String plainValue, String level, Cipher cipher)
		{
			// parse individual units of time the timestamp
			// time format is 'yyyy-MM-dd HH:mm:ss.SSS'
			String regex = "[:\\-. ]";
			String[] split = plainValue.split(regex);
			String year = split[0];
			String month = split[1];
			String day = split[2];
			String hour = split[3];
			String minute = split[4];
			String second = split[5];
			String millisecond = split[6];

			switch (level)
			{
				case LOW:
					day = encryptAnnotation(key, day, cipher);
					putAnnotationPart("day", new EncryptedString(day));
					break;
				case MEDIUM:
					hour = encryptAnnotation(key, hour, cipher);
					putAnnotationPart("hour", new EncryptedString(hour));
					break;
				case HIGH:
					minute = encryptAnnotation(key, minute, cipher);
					putAnnotationPart("minute", new EncryptedString(minute));
					second = encryptAnnotation(key, second, cipher);
					putAnnotationPart("second", new EncryptedString(second));
					millisecond = encryptAnnotation(key, millisecond, cipher);
					putAnnotationPart("millisecond", new EncryptedString(millisecond));
					break;
			}

			// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
			String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
					minute + ":" + second + "." + millisecond;
			putAnnotationValue(timestamp);
			return timestamp;
		}

		@Override
		public String decrypt(String key, String encryptedValue, String level, Cipher cipher)
		{
			// parse individual units of time the timestamp
			// time format is 'yyyy-MM-dd HH:mm:ss.SSS'
			String regex = "[:\\-. ]";
			String[] split = encryptedValue.split(regex);
			String year = split[0];
			String month = split[1];
			String day = split[2];
			String hour = split[3];
			String minute = split[4];
			String second = split[5];
			String millisecond = split[6];

			switch (level)
			{

				case LOW:
					String decryptedDay = decryptAnnotation(key, day, cipher);
					if(!decryptedDay.equals(day))
					{
						putAnnotationPart("day", new PlainString(decryptedDay));
					}
					else
					{
						putAnnotationPart("day", new PlainString("<encrypted>"));
					}
					day = decryptedDay;
					break;

				case MEDIUM:
					String decryptedHour = decryptAnnotation(key, hour, cipher);
					if(!decryptedHour.equals(hour))
					{
						putAnnotationPart("hour", new PlainString(decryptedHour));
					}
					else
					{
						putAnnotationPart("hour", new PlainString("<encrypted>"));
					}
					hour = decryptedHour;
					break;

				case HIGH:
					String decryptedMinute = decryptAnnotation(key, minute, cipher);
					if(!decryptedMinute.equals(minute))
					{
						putAnnotationPart("minute", new PlainString(decryptedMinute));
					}
					else
					{
						putAnnotationPart("minute", new PlainString("<encrypted>"));
					}
					minute = decryptedMinute;

					String decryptedSecond = decryptAnnotation(key, second, cipher);
					if(!decryptedSecond.equals(second))
					{
						putAnnotationPart("second", new PlainString(decryptedSecond));
					}
					else
					{
						putAnnotationPart("second", new PlainString("<encrypted>"));
					}
					second = decryptedSecond;

					String decryptedMillisecond = decryptAnnotation(key, millisecond, cipher);
					if(!decryptedMillisecond.equals(millisecond))
					{
						putAnnotationPart("millisecond", new PlainString(decryptedMillisecond));
					}
					else
					{
						putAnnotationPart("millisecond", new PlainString("<encrypted>"));
					}
					millisecond = decryptedMillisecond;
					break;
			}

			// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
			String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
					minute + ":" + second + "." + millisecond;
			putAnnotationValue(timestamp);
			return timestamp;
		}
	}

	public static class EncryptedIPAddress extends AnnotationValue
	{
		public EncryptedIPAddress(String value)
		{
			annotationValue = value;
		}

		@Override
		public String getAnnotationValue()
		{
			return annotationValue;
		}

		@Override
		public void putAnnotationValue(String value)
		{
			annotationValue = value;
		}

		@Override
		public Map<String, AnnotationValue> getAnnotationParts()
		{
			return null;
		}

		@Override
		public void putAnnotationPart(String partKey, AnnotationValue value)
		{

		}

		@Override
		public String encrypt(String key, String plainValue, String level, Cipher cipher)
		{
			String[] subnets = plainValue.split("\\.");
			String encryptedValue;
			switch (level)
			{
				case LOW:
					subnets[1] = encryptAnnotation(key, subnets[1], cipher);
					break;
				case MEDIUM:
					subnets[2] = encryptAnnotation(key, subnets[2], cipher);
					break;
				case HIGH:
					subnets[3] = encryptAnnotation(key, subnets[3], cipher);
					break;
			}
			encryptedValue = String.join(".", subnets);
			putAnnotationValue(encryptedValue);
			return encryptedValue;
		}

		@Override
		public String decrypt(String key, String encryptedValue, String level, Cipher cipher)
		{
			String[] subnets = encryptedValue.split("\\.");
			String decryptedValue;
			switch (level)
			{
				case LOW:
					subnets[1] = decryptAnnotation(key, subnets[1], cipher);
					break;
				case MEDIUM:
					subnets[2] = decryptAnnotation(key, subnets[2], cipher);
					break;
				case HIGH:
					subnets[3] = decryptAnnotation(key, subnets[3], cipher);
					break;
			}
			decryptedValue = String.join(".", subnets);
			putAnnotationValue(decryptedValue);
			return decryptedValue;
		}
	}

	public static class EncryptedPath extends AnnotationValue
	{
		public EncryptedPath(String value)
		{
			annotationValue = value;
		}

		@Override
		public String getAnnotationValue()
		{
			return annotationValue;
		}

		@Override
		public void putAnnotationValue(String value)
		{
			annotationValue = value;
		}

		@Override
		public Map<String, AnnotationValue> getAnnotationParts()
		{
			return null;
		}

		@Override
		public void putAnnotationPart(String partKey, AnnotationValue value)
		{
		}

		@Override
		public String encrypt(String key, String plainValue, String level, Cipher cipher)
		{
			String[] subpaths = plainValue.split(FILE_SEPARATOR, 5);
			String encryptedValue;
			int numpaths = subpaths.length;
			switch (level)
			{
				case LOW:
					if (numpaths > 2)
					{
						subpaths[2] = encryptAnnotation(key, subpaths[2], cipher);
					}
					break;
				case MEDIUM:
					if (numpaths > 3)
					{
						subpaths[3] = encryptAnnotation(key, subpaths[3], cipher);
					}
					break;
				case HIGH:
					if (numpaths > 4)
					{
						subpaths[4] = encryptAnnotation(key, subpaths[4], cipher);
					}
					break;
			}
			encryptedValue = String.join(FILE_SEPARATOR, subpaths);
			putAnnotationValue(encryptedValue);
			return encryptedValue;
		}

		@Override
		public String decrypt(String key, String encryptedValue, String level, Cipher cipher)
		{
			String[] subpaths = encryptedValue.split(FILE_SEPARATOR, 5);
			int numpaths = subpaths.length;
			String decryptedValue;
			switch (level)
			{
				case LOW:
					if (numpaths > 2)
					{
						subpaths[2] = decryptAnnotation(key, subpaths[2], cipher);
					}
					break;
				case MEDIUM:
					if (numpaths > 3)
					{
						subpaths[3] = decryptAnnotation(key, subpaths[3], cipher);
					}
					break;
				case HIGH:
					if (numpaths > 4)
					{
						subpaths[4] = decryptAnnotation(key, subpaths[4], cipher);
					}
					break;
			}
			decryptedValue = String.join(FILE_SEPARATOR, subpaths);
			putAnnotationValue(decryptedValue);
			return decryptedValue;
		}
	}

	public static class EncryptedVertex extends AbstractVertex
	{
		private final Map<String, AnnotationValue> encryptedAnnotations = new HashMap<>();

		@Override
		public Map<String, String> getAnnotations()
		{
			// getAnnotations creates annotations in String, String format
			// lazily when requested by user
			for(Map.Entry<String, AnnotationValue> encryptedAnnotation:
					this.encryptedAnnotations.entrySet())
			{
				String key = encryptedAnnotation.getKey();
				AnnotationValue annotationValue = encryptedAnnotation.getValue();
				String value = annotationValue.getAnnotationValue();
				annotations.put(key, value);
			}
			return this.annotations;
		}

		@Override
		public String getAnnotation(String key)
		{
			AnnotationValue annotation = this.encryptedAnnotations.get(key);
			if(annotation != null)
				return annotation.getAnnotationValue();
			return null;
		}

		public Map<String, AnnotationValue> getEncryptedAnnotations()
		{
			return encryptedAnnotations;
		}

		public void addEncryptedAnnotations(Map<String, AnnotationValue> newAnnotations)
		{
			for (Map.Entry<String, AnnotationValue> currentEntry : newAnnotations.entrySet())
			{
				String key = currentEntry.getKey();
				AnnotationValue value = currentEntry.getValue();
				if(value instanceof PlainString)
				{
					addAnnotation(key, new PlainString(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedString)
				{
					addAnnotation(key, new EncryptedString(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedIPAddress)
				{
					addAnnotation(key, new EncryptedIPAddress(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedPath)
				{
					addAnnotation(key, new EncryptedPath(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedTime)
				{
					AnnotationValue encryptedTime = new EncryptedTime(value.getAnnotationValue());
					for(Map.Entry<String, AnnotationValue> entry:
							value.getAnnotationParts().entrySet())
					{
						String partKey = entry.getKey();
						AnnotationValue partValue = entry.getValue();
						encryptedTime.putAnnotationPart(partKey, partValue);
					}
					addAnnotation(key, encryptedTime);
				}
			}
		}

		@Override
		public void addAnnotations(Map<String, String> newAnnotations)
		{
			for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet())
			{
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if(!StringUtils.isNullOrEmpty(key))
				{
					if(value == null)
					{
						value = "";
					}
					this.encryptedAnnotations.put(key, new PlainString(value));
				}
			}
		}

		@Override
		public void addAnnotation(String key, String value)
		{
			AnnotationValue annotationValue = this.encryptedAnnotations.get(key);
			annotationValue.putAnnotationValue(value);
		}

		public void addAnnotation(String key, AnnotationValue value)
		{
			this.encryptedAnnotations.put(key, value);
		}

		public void addAnnotationPart(String key, String part, AnnotationValue value)
		{
			AnnotationValue annotation = this.encryptedAnnotations.get(key);
			annotation.putAnnotationPart(part, value);
		}

		@Override
		public String toString()
		{
			getAnnotations();
			return super.toString();
		}
	}

	public static class EncryptedEdge extends AbstractEdge
	{
		private final Map<String, AnnotationValue> encryptedAnnotations = new HashMap<>();

		public EncryptedEdge(AbstractVertex childVertex, AbstractVertex parentVertex)
		{
			setChildVertex(childVertex);
			setParentVertex(parentVertex);
		}

		@Override
		public Map<String, String> getAnnotations()
		{
			// getAnnotations creates annotations in String, String format
			// lazily when requested by user
			for(Map.Entry<String, AnnotationValue> encryptedAnnotation:
					this.encryptedAnnotations.entrySet())
			{
				String key = encryptedAnnotation.getKey();
				AnnotationValue annotationValue = encryptedAnnotation.getValue();
				String value = annotationValue.getAnnotationValue();
				annotations.put(key, value);
			}
			return this.annotations;
		}

		@Override
		public String getAnnotation(String key)
		{
			AnnotationValue annotation = this.encryptedAnnotations.get(key);
			if(annotation != null)
				return annotation.getAnnotationValue();
			return null;
		}

		public Map<String, AnnotationValue> getEncryptedAnnotations()
		{
			return encryptedAnnotations;
		}

		public void addEncryptedAnnotations(Map<String, AnnotationValue> newAnnotations)
		{
			for (Map.Entry<String, AnnotationValue> currentEntry : newAnnotations.entrySet())
			{
				String key = currentEntry.getKey();
				AnnotationValue value = currentEntry.getValue();
				if(value instanceof PlainString)
				{
					addAnnotation(key, new PlainString(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedString)
				{
					addAnnotation(key, new EncryptedString(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedIPAddress)
				{
					addAnnotation(key, new EncryptedIPAddress(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedPath)
				{
					addAnnotation(key, new EncryptedPath(value.getAnnotationValue()));
				}
				else if(value instanceof EncryptedTime)
				{
					AnnotationValue encryptedTime = new EncryptedTime(value.getAnnotationValue());
					for(Map.Entry<String, AnnotationValue> entry:
							value.getAnnotationParts().entrySet())
					{
						String partKey = entry.getKey();
						AnnotationValue partValue = entry.getValue();
						encryptedTime.putAnnotationPart(partKey, partValue);
					}
					addAnnotation(key, encryptedTime);
				}
			}
		}

		@Override
		public void addAnnotations(Map<String, String> newAnnotations)
		{
			for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet())
			{
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if(!StringUtils.isNullOrEmpty(key))
				{
					if(value == null)
					{
						value = "";
					}
					this.encryptedAnnotations.put(key, new PlainString(value));
				}
			}
		}

		@Override
		public void addAnnotation(String key, String value)
		{
			AnnotationValue annotationValue = this.encryptedAnnotations.get(key);
			annotationValue.putAnnotationValue(value);
		}

		public void addAnnotation(String key, AnnotationValue value)
		{
			this.encryptedAnnotations.put(key, value);
		}

		public void addAnnotationPart(String key, String part, AnnotationValue value)
		{
			AnnotationValue annotation = this.encryptedAnnotations.get(key);
			annotation.putAnnotationPart(part, value);
		}

		@Override
		public String toString()
		{
			getAnnotations();
			return super.toString();
		}
	}

	public String getLowKey()
	{
		return lowKey;
	}

	public void setLowKey(String lowKey)
	{
		this.lowKey = lowKey;
	}

	public String getMediumKey()
	{
		return mediumKey;
	}

	public void setMediumKey(String mediumKey)
	{
		this.mediumKey = mediumKey;
	}

	public String getHighKey()
	{
		return highKey;
	}

	public void setHighKey(String highKey)
	{
		this.highKey = highKey;
	}

	/*
	Converts unix timestamp into human readable time of format
	'yyyy-MM-dd HH:mm:ss.SSS'
	 */
	private static void convertUnixTime(AbstractEdge edge)
	{
		String unixTime = edge.getAnnotation(OPMConstants.EDGE_TIME);
		Date date = new Date(Double.valueOf(Double.parseDouble(unixTime) * 1000).longValue());
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		String year = String.valueOf(calendar.get(Calendar.YEAR));
		String month = String.valueOf(calendar.get(Calendar.MONTH) + 1); // zero-based indexing
		String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
		String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
		String minute = String.valueOf(calendar.get(Calendar.MINUTE));
		String second = String.valueOf(calendar.get(Calendar.SECOND));
		String millisecond = String.valueOf(calendar.get(Calendar.MILLISECOND));

		// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
		String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
				minute + ":" + second + "." + millisecond;
		edge.addAnnotation(OPMConstants.EDGE_TIME, timestamp);
	}

	public static ABEGraph copy(Graph graph, boolean encryption)
	{
		Map<String, AbstractVertex> vertexMap = new HashMap<>();
		ABEGraph newGraph = new ABEGraph();
		for(AbstractVertex vertex : graph.vertexSet())
		{
			AbstractVertex newVertex = copyVertex(vertex, encryption);
			newGraph.putVertex(newVertex);
			vertexMap.put(newVertex.bigHashCode(), newVertex);
		}
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = copyEdge(edge, encryption);
			if(encryption)
				convertUnixTime(newEdge);
			newEdge.setChildVertex(vertexMap.get(edge.getChildVertex().bigHashCode()));
			newEdge.setParentVertex(vertexMap.get(edge.getParentVertex().bigHashCode()));
			newGraph.putEdge(newEdge);
		}
		return newGraph;
	}

	@Override
	public boolean putVertex(AbstractVertex vertex)
	{
		vertexSet().add(vertex);
		return true;
	}

	@Override
	public boolean putEdge(AbstractEdge edge)
	{
		edgeSet().add(edge);
		return true;
	}

	public static AbstractVertex copyVertex(AbstractVertex vertex, boolean encryption)
	{
		EncryptedVertex newVertex = new EncryptedVertex();
		if(encryption)
		{
			newVertex.addAnnotations(vertex.getAnnotations());
		}
		else
		{
			newVertex.addEncryptedAnnotations(((EncryptedVertex)vertex).getEncryptedAnnotations());
		}
		newVertex.setDepth(vertex.getDepth());
		return newVertex;
	}

	public static AbstractEdge copyEdge(AbstractEdge edge, boolean encryption)
	{
		EncryptedEdge newEdge = new EncryptedEdge(null, null);
		if(encryption)
		{
			newEdge.addAnnotations(edge.getAnnotations());
		}
		else
		{
			newEdge.addEncryptedAnnotations(((EncryptedEdge)edge).getEncryptedAnnotations());
		}
		return newEdge;
	}
}
