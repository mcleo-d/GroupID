import * as apiCalls from "../apiCalls";
import axios from 'axios';
import { getUserJWT } from 'symphony-app-authentication-fe';

jest.mock('axios');
jest.mock('symphony-app-authentication-fe');

const mockUserId = 123456789;

const mockTicket = {
    userId: mockUserId,
    url: 'http://testurl.test/ticket1234321',
    entity: {
        claimUrl: 'http://testurl.test/ticket1234321/claim',
        joinUrl: 'http://testurl.test/ticket1234321/join'
    }
};

const mockAttachment = {
    attachmentUrl: mockTicket.url + '98789',
    approveUrl: mockTicket.url + '98789/approve',
    denyUrl: mockTicket.url + '98789/deny'
};

const mockError = new Promise(() => {
    throw {
        response: {
            message: 'intentional test error!',
            status: 100
        }
    };
});

const mockEmptyError = new Promise(() => {
    throw {};
});

describe('API Calls', () => {
    describe('Successful scenarios:', () => {
        beforeEach(() => {
            axios.mockClear();
            axios.mockResolvedValue();
            getUserJWT.mockReturnValue('23JWT798');
        });
        it('Should make a claimTicket call', () => {
            apiCalls.claimTicket(mockTicket);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'post',
                url: mockTicket.entity.claimUrl,
                params: { agentId: mockUserId }
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
        it('Should make a joinConversation call', () => {
            apiCalls.joinConversation(mockTicket);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'post',
                url: mockTicket.entity.joinUrl,
                params: { agentId: mockUserId }
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
        it('Should make a getTicket call', () => {
            apiCalls.getTicket(mockTicket.url);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'get',
                url: mockTicket.url
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
        it('Should make a approveAttachment call', () => {
            apiCalls.approveAttachment(mockAttachment.approveUrl, mockUserId);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'post',
                url: mockAttachment.approveUrl,
                params: { userId: mockUserId }
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
        it('Should make a denyAttachment call', () => {
            apiCalls.denyAttachment(mockAttachment.denyUrl, mockUserId);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'post',
                url: mockAttachment.denyUrl,
                params: { userId: mockUserId }
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
        it('Should make a searchAttachment call', () => {
            apiCalls.searchAttachment(mockAttachment.attachmentUrl);
            const expectedArg = {
                headers: {
                    Authorization: "Bearer 23JWT798"
                },
                method: 'get',
                url: mockAttachment.attachmentUrl
            };

            expect(axios.mock.calls.length).toBe(1);
            expect(axios.mock.calls[0]).toEqual([expectedArg]);
        });
    });
    describe('Failing scenarios:', () => {
        it('Should return error 100', async () => {
            axios.mockClear();
            axios.mockResolvedValue(mockError);
            expect.assertions(6);

            await expect(apiCalls.claimTicket(mockTicket).catch(rsp => { return rsp.message })).resolves.toEqual('100');
            await expect(apiCalls.joinConversation(mockTicket).catch(rsp => { return rsp.message })).resolves.toEqual('100');
            await expect(apiCalls.getTicket(mockTicket.url).catch(rsp => { return rsp.message })).resolves.toEqual('100');
            await expect(apiCalls.approveAttachment(mockAttachment.approveUrl, mockUserId).catch(rsp => { return rsp.message })).resolves.toEqual('100');
            await expect(apiCalls.denyAttachment(mockAttachment.denyUrl, mockUserId).catch(rsp => { return rsp.message })).resolves.toEqual('100');
            await expect(apiCalls.searchAttachment(mockAttachment.attachmentUrl).catch(rsp => { return rsp.message })).resolves.toEqual('100');
        });
        it('Should return default error 500', async () => {
            axios.mockClear();
            axios.mockResolvedValue(mockEmptyError);
            expect.assertions(1);

            await expect(apiCalls.claimTicket(mockTicket).catch(rsp => { return rsp.message })).resolves.toEqual('500');
        })
    });
});