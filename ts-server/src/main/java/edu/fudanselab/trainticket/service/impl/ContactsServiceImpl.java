package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.Contacts;
import edu.fudanselab.trainticket.service.ContactsService;
import edu.fudanselab.trainticket.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import edu.fudanselab.trainticket.repository.ContactsRepository;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author fdse
 */
@Service
public class ContactsServiceImpl implements ContactsService {

    @Autowired
    private ContactsRepository contactsRepository;

    String success = "Success";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactsServiceImpl.class);

    @Override
    public Response findContactsById(String id, HttpHeaders headers) {
        LOGGER.info("FIND CONTACTS BY ID: " + id);
        try {
            Contacts contacts = contactsRepository.findById(id).orElse(null);
            if (contacts != null) {
                return new Response<>(1, success, contacts);
            } else {
                LOGGER.error("[findContactsById][contactsRepository.findById][No contacts according to contactsId][contactsId: {}]", id);
                return new Response<>(0, "No contacts according to contacts id", null);
            }
        } catch (Exception e) {
            LOGGER.error("[findContactsById][Exception][contactsId: {}][error: {}]", id, e.getMessage(), e);
            return new Response<>(0, "Find contacts failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Response findContactsByAccountId(String accountId, HttpHeaders headers) {
        try {
            ArrayList<Contacts> arr = contactsRepository.findByAccountId(accountId);
            ContactsServiceImpl.LOGGER.info("[findContactsByAccountId][Query Contacts][Result Size: {}]", arr.size());
            return new Response<>(1, success, arr);
        } catch (Exception e) {
            LOGGER.error("[findContactsByAccountId][Exception][accountId: {}][error: {}]", accountId, e.getMessage(), e);
            return new Response<>(0, "Find contacts by accountId failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Response createContacts(Contacts contacts, HttpHeaders headers) {
        try {
            // 修复：修正方法名和参数（应该是AccountId + DocumentNumber + DocumentType）
            Contacts contactsTemp = contactsRepository.findByAccountIdAndDocumentNumberAndDocumentType(
                    contacts.getAccountId(),
                    contacts.getDocumentNumber(),
                    contacts.getDocumentType()
            );
            if (contactsTemp != null) {
                ContactsServiceImpl.LOGGER.warn("[createContacts][Init Contacts, Already Exists][Id: {}]", contacts.getId());
                return new Response<>(0, "Already Exists", contactsTemp);
            } else {
                contactsRepository.save(contacts);
                return new Response<>(1, "Create Success", null);
            }
        } catch (Exception e) {
            LOGGER.error("[createContacts][Exception][contacts: {}][error: {}]", contacts, e.getMessage(), e);
            return new Response<>(0, "Create contacts failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Response create(Contacts addContacts, HttpHeaders headers) {
        try {
            // 修复：修正方法名和参数
            Contacts c = contactsRepository.findByAccountIdAndDocumentNumberAndDocumentType(
                    addContacts.getAccountId(),
                    addContacts.getDocumentNumber(),
                    addContacts.getDocumentType()
            );

            if (c != null) {
                ContactsServiceImpl.LOGGER.warn("[Contacts-Add&Delete-Service.create][AddContacts][Fail.Contacts already exists][contactId: {}]", addContacts.getId());
                return new Response<>(0, "Contacts already exists", null);
            } else {
                Contacts contacts = contactsRepository.save(addContacts);
                ContactsServiceImpl.LOGGER.info("[Contacts-Add&Delete-Service.create][AddContacts Success]");
                return new Response<>(1, "Create contacts success", contacts);
            }
        } catch (Exception e) {
            LOGGER.error("[create][Exception][addContacts: {}][error: {}]", addContacts, e.getMessage(), e);
            return new Response<>(0, "Create contacts failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Response delete(String contactsId, HttpHeaders headers) {
        try {
            // 先检查联系人是否存在
            Contacts contacts = contactsRepository.findById(contactsId).orElse(null);
            if (contacts == null) {
                LOGGER.warn("[delete][Contacts not found][contactsId: {}]", contactsId);
                return new Response<>(0, "Contacts not found", contactsId);
            }

            // 使用 delete() 方法删除已查询到的实体，避免 deleteById() 的内部查询导致的异常
            contactsRepository.delete(contacts);

            ContactsServiceImpl.LOGGER.info("[Contacts-Add&Delete-Service][DeleteContacts Success][contactsId: {}]", contactsId);
            return new Response<>(1, "Delete success", contactsId);
        } catch (EmptyResultDataAccessException e) {
            // 捕获删除不存在数据的异常
            LOGGER.warn("[delete][Contacts not found][contactsId: {}]", contactsId);
            return new Response<>(0, "Contacts not found", contactsId);
        } catch (DataAccessException e) {
            // 捕获数据库相关异常（如外键约束）
            LOGGER.error("[delete][Database Exception][contactsId: {}][error: {}]", contactsId, e.getMessage(), e);
            return new Response<>(0, "Delete failed (database error): " + e.getMessage(), contactsId);
        } catch (Exception e) {
            // 捕获所有其他异常
            LOGGER.error("[delete][Exception][contactsId: {}][error: {}]", contactsId, e.getMessage(), e);
            return new Response<>(0, "Delete failed: " + e.getMessage(), contactsId);
        }
    }

    @Override
    public Response modify(Contacts contacts, HttpHeaders headers) {
        try {
            // 修复：移除无效的headers = null
            Response oldContactResponse = findContactsById(contacts.getId(), headers);
            LOGGER.info(oldContactResponse.toString());

            // 修复：先判断是否为null，再强转
            if (oldContactResponse.getData() == null) {
                ContactsServiceImpl.LOGGER.error("[Contacts-Modify-Service.modify][ModifyContacts][Fail.Contacts not found][contactId: {}]", contacts.getId());
                return new Response<>(0, "Contacts not found", null);
            }

            Contacts oldContacts = (Contacts) oldContactResponse.getData();
            oldContacts.setName(contacts.getName());
            oldContacts.setDocumentType(contacts.getDocumentType());
            oldContacts.setDocumentNumber(contacts.getDocumentNumber());
            oldContacts.setPhoneNumber(contacts.getPhoneNumber());
            contactsRepository.save(oldContacts);

            ContactsServiceImpl.LOGGER.info("[Contacts-Modify-Service.modify][ModifyContacts Success][contactId: {}]", contacts.getId());
            return new Response<>(1, "Modify success", oldContacts);
        } catch (ClassCastException e) {
            LOGGER.error("[modify][ClassCastException][contactsId: {}][error: {}]", contacts.getId(), e.getMessage(), e);
            return new Response<>(0, "Modify failed (data type error): " + e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.error("[modify][Exception][contactsId: {}][error: {}]", contacts.getId(), e.getMessage(), e);
            return new Response<>(0, "Modify failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Response getAllContacts(HttpHeaders headers) {
        try {
            ArrayList<Contacts> contacts = contactsRepository.findAll();
            if (contacts != null && !contacts.isEmpty()) {
                return new Response<>(1, success, contacts);
            } else {
                LOGGER.warn("[getAllContacts][contactsRepository.findAll][No contacts found]");
                return new Response<>(0, "No content", null);
            }
        } catch (Exception e) {
            LOGGER.error("[getAllContacts][Exception][error: {}]", e.getMessage(), e);
            return new Response<>(0, "Get all contacts failed: " + e.getMessage(), null);
        }
    }

}