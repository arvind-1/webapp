package org.literacyapp.web.content.multimedia.image;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;

import org.apache.log4j.Logger;
import org.literacyapp.dao.ImageDao;
import org.literacyapp.dao.LetterDao;
import org.literacyapp.dao.NumberDao;
import org.literacyapp.dao.WordDao;
import org.literacyapp.model.Contributor;
import org.literacyapp.model.content.Letter;
import org.literacyapp.model.content.Word;
import org.literacyapp.model.content.multimedia.Image;
import org.literacyapp.model.enums.ContentLicense;
import org.literacyapp.model.enums.content.ImageFormat;
import org.literacyapp.model.enums.content.LiteracySkill;
import org.literacyapp.model.enums.content.NumeracySkill;
import org.literacyapp.util.ImageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.ByteArrayMultipartFileEditor;

@Controller
@RequestMapping("/content/multimedia/image/edit")
public class ImageEditController {
    
    private final Logger logger = Logger.getLogger(getClass());
    
    @Autowired
    private ImageDao imageDao;
    
    @Autowired
    private LetterDao letterDao;
    
    @Autowired
    private NumberDao numberDao;
    
    @Autowired
    private WordDao wordDao;

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String handleRequest(
            HttpSession session,
            Model model, 
            @PathVariable Long id) {
    	logger.info("handleRequest");
        
        Contributor contributor = (Contributor) session.getAttribute("contributor");
        
        Image image = imageDao.read(id);
        model.addAttribute("image", image);
        
        model.addAttribute("contentLicenses", ContentLicense.values());
        
        model.addAttribute("literacySkills", LiteracySkill.values());
        model.addAttribute("numeracySkills", NumeracySkill.values());
        
        model.addAttribute("letters", letterDao.readAllOrdered(contributor.getLocale()));
        model.addAttribute("numbers", numberDao.readAllOrdered(contributor.getLocale()));
        model.addAttribute("words", wordDao.readAllOrdered(contributor.getLocale()));

        return "content/multimedia/image/edit";
    }
    
    @RequestMapping(value = "/{id}", method = RequestMethod.POST)
    public String handleSubmit(
            HttpSession session,
            Image image,
            @RequestParam("bytes") MultipartFile multipartFile,
            BindingResult result,
            Model model) {
    	logger.info("handleSubmit");
        
        Contributor contributor = (Contributor) session.getAttribute("contributor");
        
        if (StringUtils.isBlank(image.getTitle())) {
            result.rejectValue("title", "NotNull");
        } else {
            Image existingImage = imageDao.read(image.getTitle(), image.getLocale());
            if ((existingImage != null) && !existingImage.getId().equals(image.getId())) {
                result.rejectValue("title", "NonUnique");
            }
        }
        
        try {
            byte[] bytes = multipartFile.getBytes();
            if (multipartFile.isEmpty() || (bytes == null) || (bytes.length == 0)) {
                result.rejectValue("bytes", "NotNull");
            } else {
                String originalFileName = multipartFile.getOriginalFilename();
                logger.info("originalFileName: " + originalFileName);
                if (originalFileName.toLowerCase().endsWith(".png")) {
                    image.setImageFormat(ImageFormat.PNG);
                } else if (originalFileName.toLowerCase().endsWith(".jpg") || originalFileName.toLowerCase().endsWith(".jpeg")) {
                    image.setImageFormat(ImageFormat.JPG);
                } else if (originalFileName.toLowerCase().endsWith(".gif")) {
                    image.setImageFormat(ImageFormat.GIF);
                } else {
                    result.rejectValue("bytes", "typeMismatch");
                }

                if (image.getImageFormat() != null) {
                    String contentType = multipartFile.getContentType();
                    logger.info("contentType: " + contentType);
                    image.setContentType(contentType);

                    image.setBytes(bytes);

                    if (image.getImageFormat() != ImageFormat.GIF) {
                        int width = ImageHelper.getWidth(bytes);
                        logger.info("width: " + width + "px");

                        if (width < ImageHelper.MINIMUM_WIDTH) {
                            result.rejectValue("bytes", "image.too.small");
                            image.setBytes(null);
                        } else {
                            if (width > ImageHelper.MINIMUM_WIDTH) {
                                bytes = ImageHelper.scaleImage(bytes, ImageHelper.MINIMUM_WIDTH);
                                image.setBytes(bytes);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e);
        }
        
        if (result.hasErrors()) {
            model.addAttribute("image", image);
            model.addAttribute("contentLicenses", ContentLicense.values());
            model.addAttribute("literacySkills", LiteracySkill.values());
            model.addAttribute("numeracySkills", NumeracySkill.values());
            model.addAttribute("letters", letterDao.readAllOrdered(contributor.getLocale()));
            model.addAttribute("numbers", numberDao.readAllOrdered(contributor.getLocale()));
            model.addAttribute("words", wordDao.readAllOrdered(contributor.getLocale()));
            return "content/multimedia/image/edit";
        } else {
            image.setTitle(image.getTitle().toLowerCase());
            image.setTimeLastUpdate(Calendar.getInstance());
            image.setRevisionNumber(image.getRevisionNumber() + 1);
            imageDao.update(image);
            
            return "redirect:/content/multimedia/image/list#" + image.getId();
        }
    }
    
    /**
     * See http://www.mkyong.com/spring-mvc/spring-mvc-failed-to-convert-property-value-in-file-upload-form/
     * <p></p>
     * Fixes this error message:
     * "Cannot convert value of type [org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile] to required type [byte] for property 'bytes[0]'"
     */
    @InitBinder
    protected void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws ServletException {
    	logger.info("initBinder");
    	binder.registerCustomEditor(byte[].class, new ByteArrayMultipartFileEditor());
    }
    
    @RequestMapping(value = "/{id}/add-content-label", method = RequestMethod.POST)
    @ResponseBody
    public String handleAddContentLabelRequest(
            HttpServletRequest request,
            @PathVariable Long id) {
    	logger.info("handleAddContentLabelRequest");
        
        logger.info("id: " + id);
        Image image = imageDao.read(id);
        
        String letterIdParameter = request.getParameter("letterId");
        logger.info("letterIdParameter: " + letterIdParameter);
        if (StringUtils.isNotBlank(letterIdParameter)) {
            Long letterId = Long.valueOf(letterIdParameter);
            Letter letter = letterDao.read(letterId);
            List<Letter> letters = image.getLetters();
            logger.info("letters.contains(letter): " + letters.contains(letter));
            if (!letters.contains(letter)) {
                letters.add(letter);
                image.setLetters(letters);
                imageDao.update(image);
            }
        }
        
        String numberIdParameter = request.getParameter("numberId");
        logger.info("numberIdParameter: " + numberIdParameter);
        if (StringUtils.isNotBlank(numberIdParameter)) {
            Long numberId = Long.valueOf(numberIdParameter);
            org.literacyapp.model.content.Number number = numberDao.read(numberId);
            List<org.literacyapp.model.content.Number> numbers = image.getNumbers();
            logger.info("numbers.contains(number): " + numbers.contains(number));
            if (!numbers.contains(number)) {
                numbers.add(number);
                image.setNumbers(numbers);
                imageDao.update(image);
            }
        }
        
        String wordIdParameter = request.getParameter("wordId");
        logger.info("wordIdParameter: " + wordIdParameter);
        if (StringUtils.isNotBlank(wordIdParameter)) {
            Long wordId = Long.valueOf(wordIdParameter);
            Word word = wordDao.read(wordId);
            List<Word> words = image.getWords();
            logger.info("words.contains(word): " + words.contains(word));
            if (!words.contains(word)) {
                words.add(word);
                image.setWords(words);
                imageDao.update(image);
            }
        }
        
        return "success";
    }
    
    @RequestMapping(value = "/{id}/remove-content-label", method = RequestMethod.POST)
    @ResponseBody
    public String handleRemoveContentLabelRequest(
            HttpServletRequest request,
            @PathVariable Long id) {
    	logger.info("handleRemoveContentLabelRequest");
        
        logger.info("id: " + id);
        Image image = imageDao.read(id);
        
        String letterIdParameter = request.getParameter("letterId");
        logger.info("letterIdParameter: " + letterIdParameter);
        if (StringUtils.isNotBlank(letterIdParameter)) {
            Long letterId = Long.valueOf(letterIdParameter);
            Letter letter = letterDao.read(letterId);
            List<Letter> letters = image.getLetters();
            logger.info("letters.contains(letter): " + letters.contains(letter));
            for (int index = 0; index < letters.size(); index++) {
                Letter existingLetter = letters.get(index);
                logger.info("letterId.equals(existingLetter.getId()): " + letterId.equals(existingLetter.getId()));
                if (letterId.equals(existingLetter.getId())) {
                    letters.remove(index);
                    image.setLetters(letters);
                    imageDao.update(image);
                    break;
                }
            }
        }
        
        String numberIdParameter = request.getParameter("numberId");
        logger.info("numberIdParameter: " + numberIdParameter);
        if (StringUtils.isNotBlank(numberIdParameter)) {
            Long numberId = Long.valueOf(numberIdParameter);
            org.literacyapp.model.content.Number number = numberDao.read(numberId);
            List<org.literacyapp.model.content.Number> numbers = image.getNumbers();
            logger.info("numbers.contains(number): " + numbers.contains(number));
            for (int index = 0; index < numbers.size(); index++) {
                org.literacyapp.model.content.Number existingNumber = numbers.get(index);
                logger.info("numberId.equals(existingNumber.getId()): " + numberId.equals(existingNumber.getId()));
                if (numberId.equals(existingNumber.getId())) {
                    numbers.remove(index);
                    image.setNumbers(numbers);
                    imageDao.update(image);
                    break;
                }
            }
        }
        
        String wordIdParameter = request.getParameter("wordId");
        logger.info("wordIdParameter: " + wordIdParameter);
        if (StringUtils.isNotBlank(wordIdParameter)) {
            Long wordId = Long.valueOf(wordIdParameter);
            Word word = wordDao.read(wordId);
            List<Word> words = image.getWords();
            logger.info("words.contains(word): " + words.contains(word));
            for (int index = 0; index < words.size(); index++) {
                Word existingWord = words.get(index);
                logger.info("wordId.equals(existingWord.getId()): " + wordId.equals(existingWord.getId()));
                if (wordId.equals(existingWord.getId())) {
                    words.remove(index);
                    image.setWords(words);
                    imageDao.update(image);
                    break;
                }
            }
        }
        
        return "success";
    }
}
